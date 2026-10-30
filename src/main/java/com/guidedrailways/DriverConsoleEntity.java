package com.guidedrailways;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DriverConsoleEntity extends BlockEntity
    implements MenuProvider,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
  final ScheduleController schedule = new ScheduleController(this);
  java.util.UUID consoleId = java.util.UUID.randomUUID();
  long placementOrder;
  java.util.UUID operator;
  int steer;
  long steerAt;
  boolean active, enabled, emergency, observing;
  int direction = 1, notch = -4;

  /** Why this console's train is not moving; computed when the payload is sent. */
  ConsoleStatus statusReason = ConsoleStatus.NO_BODY;

  private long nextStatusLog;

  /** True while auto align is allowed to creep the train to its stop. */
  private boolean aligning;

  boolean aligning() {
    return aligning;
  }

  /**
   * What this console is currently trying to do, derived from the flags it already keeps.
   *
   * <p>Derived rather than stored: storing it would create a second source of truth next to the flags, which is the
   * arrangement this type exists to remove.
   */
  ConsoleIntent intent() {
    return ConsoleIntent.of(bound, aligning, schedule.running(), observing, active);
  }

  void stopAligning() {
    aligning = false;
  }

  /** Signed distance to the stop for the align controller, or NaN when unknown. */
  double alignOffset() {
    double offset = resolveOffset();
    return Double.isNaN(offset) ? Double.NaN : offset;
  }

  /** Signed distance to the stop last sent to clients; zero when unknown. */
  double stopOffset;

  /** Cached text for the second screen line, rebuilt only when the offset moves enough to matter. */
  private String offsetText = "";
  private double offsetTextSource = Double.NaN;
  private long pressedUntil;

  /** Whether this console resolves to a live body; the lamps go dark when it does not. */
  boolean bound;
  private int[] gearSpeeds;
  private String trainName = "";
  private TrainOwner trainOwner = TrainOwner.UNKNOWN;

  TrainOwner trainOwner() {
    return trainOwner;
  }

  String trainName() {
    return trainName;
  }

  boolean setTrainName(Player player, String value) {
    if (!canConfigure(player)) return false;
    String next = TrainName.normalize(value);
    if (!next.equals(trainName)) {
      trainName = next;
      updateVisual();
    }
    return true;
  }

  int maxSpeedTenths() {
    return SpeedSetting.fromRatio(TrainConfig.speedPerRpm());
  }

  int[] gearSpeeds() {
    if (!SpeedSetting.validGears(SpeedSetting.MAX, gearSpeeds))
      gearSpeeds = SpeedSetting.defaultGears(maxSpeedTenths());
    return gearSpeeds;
  }

  double manualCeiling(int gear, double available) {
    return SpeedSetting.gearCeiling(available, gear, gearSpeeds());
  }

  double targetSpeed(double rpm) {
    return Math.abs(rpm) * TrainConfig.speedPerRpm();
  }

  boolean canConfigure(Player player) {
    return configurationAllowed(player) && DriverControls.isPrimary(this);
  }

  boolean canConfigureView(Player player) {
    return configurationAllowed(player) && DriverControls.primaryView(this);
  }

  private boolean configurationAllowed(Player player) {
    return level instanceof ServerLevel
        && player.mayBuild()
        && level.mayInteract(player, worldPosition)
        && DriverControls.accessible(this, player)
        && (!active || player.getUUID().equals(operator));
  }

  boolean setGearSpeeds(Player player, int[] speeds) {
    if (!canConfigure(player) || !SpeedSetting.validGears(SpeedSetting.MAX, speeds)) return false;
    gearSpeeds = speeds.clone();
    updateVisual();
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[DriverGearSetting] console={} targetSpeedTenths={}",
            worldPosition,
            java.util.Arrays.toString(gearSpeeds));
    return true;
  }

  float lever = -1, previousLever = -1, reverser = 1;
  double actual, available;
  int supports, powered, guided;

  DriverConsoleEntity(BlockPos p, BlockState s) {
    super(Content.DRIVER_ENTITY.get(), p, s);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level instanceof ServerLevel server) DriverControls.register(server, this);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.guidedrailways.driver_console");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new DriverMenu(id, inventory, this);
  }

  void placed(ServerLevel server, Player player) {
    consoleId = java.util.UUID.randomUUID();
    trainOwner =
        player == null
            ? TrainOwner.UNKNOWN
            : new TrainOwner(player.getUUID(), player.getGameProfile().getName());
    placementOrder = ConsoleOrderData.place(server);
    setChanged();
    DriverControls.register(server, this);
  }

  void resetCopiedIdentity(java.util.UUID id, long order) {
    consoleId = id;
    placementOrder = order;
    active = false;
    observing = false;
    enabled = false;
    operator = null;
    steer = 0;
    notch = -4;
    schedule.pause(ScheduleController.CHANGED);
    updateVisual();
  }

  void clientTick() {
    previousLever = lever;
    lever += ((float) DriverModel.lever(notch) - lever) * .5f;
    reverser += (direction - reverser) * .5f;
  }

  void updateVisual() {
    reportStatus();
    setChanged();
    if (level instanceof ServerLevel server && !isRemoved()) {
      syncState();
      sendVisual(server);
      DriverNetwork.sync(this, active);
    }
  }

  /** Ticks the button stays visibly lit. */
  private static final long PRESS_TICKS = 8;

  /**
   * The single writer of the display block state.
   *
   * <p>The screen index and the pressed flag go out in one update: writing them from two places made
   * the second write carry a stale value and clear the press that had just been set. Each write costs
   * a client chunk rebuild, so a change is throttled unless it is the emergency state, which has to be
   * immediate.
   */
  private void syncState() {
    if (!(level instanceof ServerLevel)) return;
    // Lamps follow the vehicle, not the seat: an unstaffed carriage keeps showing what it was last
    // told to do, and only a console with nothing to drive goes dark.
    bound = DriverControls.body(this) != null;
    var state = getBlockState();
    if (!state.hasProperty(DriverConsoleBlock.PRESSED)) return;
    boolean wantPressed = pressedUntil > level.getGameTime();
    if (state.getValue(DriverConsoleBlock.PRESSED) == wantPressed) return;
    // Only the pressed button is worth a client chunk rebuild now that the lamps are drawn by the
    // block entity renderer every frame; the screen model never changes while driving.
    level.setBlock(
        worldPosition, state.setValue(DriverConsoleBlock.PRESSED, wantPressed), Block.UPDATE_CLIENTS);
  }

  /** Lights the emergency button for a moment, so pressing it is visible to everyone nearby. */
  void press() {
    if (level == null) return;
    pressedUntil = level.getGameTime() + PRESS_TICKS;
    syncState();
  }

  /** Releases the button once its press is over. */
  void clearPressIfDue() {
    if (pressedUntil == 0 || level == null || level.getGameTime() < pressedUntil) return;
    pressedUntil = 0;
    syncState();
  }
  /**
   * Sends the block entity payload only.
   *
   * <p>{@code sendBlockUpdated(..., UPDATE_CLIENTS)} also broadcasts a block state packet, and the
   * client answers a block state packet by rebuilding that chunk section. The lever changes every few
   * ticks while a schedule runs, so that path rebuilt the section several times a second and showed up
   * as periodic hundred-millisecond frames. The payload already carries {@code VisualNotch},
   * {@code VisualActive} and {@code VisualEmergency}, which is everything the client draws.
   */
  private void sendVisual(ServerLevel server) {
    var packet = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    for (var player :
        server.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false))
      player.connection.send(packet);
  }

  void leave() {
    active = false;
    observing = false;
    steer = 0;
    DriverNetwork.sync(this, false);
    operator = null;
    updateVisual();
  }

  void park() {
    if (schedule.present()) schedule.pause(ScheduleController.PAUSED);
    enabled = false;
    active = false;
    observing = false;
    steer = 0;
    notch = -4;
    DriverNetwork.sync(this, false);
    operator = null;
    updateVisual();
  }

  void command(Player player, int command) {
    if (!(level instanceof ServerLevel server)
        || !player.mayBuild()
        || !server.mayInteract(player, worldPosition)) return;
    if (command != 19 && !DriverControls.isPrimary(this)) {
      DriverControls.secondaryHint(player);
      return;
    }
    if (active && operator != null && !operator.equals(player.getUUID()) && command != 8) {
      hint(player, "busy");
      return;
    }
    if (observing && !DriverInputPolicy.permittedWhileObserving(command)) return;
    if (command == 19) {
      if (operator != null && operator.equals(player.getUUID())) leave();
      return;
    }
    if (command >= 20 && command <= 22) {
      if (active && player.getUUID().equals(operator)) {
        steer = command == 20 ? -1 : command == 21 ? 1 : 0;
        steerAt = server.getGameTime();
      }
      return;
    }
    int oldNotch = notch, oldDirection = direction;
    boolean oldActive = active, oldEmergency = emergency;
    if (command == 0) {
      if (!active && DriverControls.body(this) == null) {
        hint(player, "install");
        return;
      }
      if (active) leave();
      else {
        DriverControls.claim(server, this, player);
        active = true;
        observing = schedule.running() || schedule.resumePending();
        if (!observing) enabled = true;
        operator = player.getUUID();
      }
    } else if (command == 35) {
      if (DriverControls.body(this) == null) {
        hint(player, "install");
        return;
      }
      DriverControls.claim(server, this, player);
      active = true;
      observing = false;
      enabled = true;
      operator = player.getUUID();
      steer = 0;
      if (schedule.present()) schedule.manual(this);
      updateVisual();
      return;
    } else if (command == 8) {
      DriverControls.emergency(this);
    } else {
      if (!active) return;
      if (command == 9) {
        if (!DriverControls.canChangeDirection(this)) {
          hint(player, "reset_stop");
          return;
        }
        DriverControls.resetEmergency(this);
      } else if (emergency) {
        hint(player, "reset_first");
        return;
      } else if (command >= 3 && command <= 5) {
        int next = command == 3 ? 1 : command == 4 ? 0 : -1;
        if (next != direction) {
          if (!DriverControls.canChangeDirection(this)) {
            hint(player, "reverser_stop");
            return;
          }
          direction = next;
        }
      } else if (command == 40) {
        if (!bound) {
          hint(player, "install");
          return;
        }
        aligning = !aligning;
        updateVisual();
        return;
      } else if (command == 1) notch = DriverModel.step(notch, -1);
      else if (command == 2) notch = DriverModel.step(notch, 1);
      else if (command == 6) notch = 0;
      else if (command == 7) notch = -4;
      else if (DriverInputPolicy.notchCommand(command))
        notch = command - DriverInputPolicy.NOTCH_BASE;
      else return;
      // Giving a notch by hand is the driver taking the train: a schedule start leaves the console with
      // drive disabled, and without this the lever moved while the train never moved, which reads as a
      // stuck train rather than as "press the drive key".
      if (!observing) enabled = true;
      if (notch >= 0) VehiclePhysics.releaseMountBrakes(this);
    }
    if (oldNotch == notch
        && oldDirection == direction
        && oldActive == active
        && oldEmergency == emergency) return;
    updateVisual();
    var pos = DriverControls.position(this);
    server.playSound(
        null,
        pos.x,
        pos.y,
        pos.z,
        net.minecraft.sounds.SoundEvents.LEVER_CLICK,
        net.minecraft.sounds.SoundSource.BLOCKS,
        .45f,
        1 + notch * .035f);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[DriverConsole] pos={} operator={} active={} direction={} notch={} emergency={}",
            worldPosition,
            player.getUUID(),
            active,
            direction,
            notch,
            emergency);
  }

  void open(ServerPlayer player) {
    player.openMenu(this);
  }

  private void hint(Player player, String key) {
    player.displayClientMessage(Component.translatable("gui.guidedrailways.driver." + key), true);
  }

  @Override
  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  /**
   * Folds the console's own state into one reason.
   *
   * <p>Only what this entity can answer is filled in here; the switch and signal flags are set by the paths
   * that decide them, because a console has no way to know why another consist is holding a junction.
   */
  ConsoleStatus resolveStatus() {
    boolean atStop = schedule.status() == ScheduleController.WAITING;
    var body = bound ? DriverControls.body(this) : null;
    boolean stationGone = false;
    String blocked = null;
    if (body != null && getLevel() instanceof net.minecraft.server.level.ServerLevel server)
      blocked =
          SwitchBlockedReasons.reason(
              server, SwitchBlockedReasons.identityOf(server, body.getUniqueId()));
    if (body != null && getLevel() instanceof net.minecraft.server.level.ServerLevel server2)
      stationGone =
          schedule.station() != null && !StationLookup.exists(server2, schedule.station());
    return ConsoleStatus.of(
        new ConsoleStatus.Inputs(
            bound,
            stationGone,
            schedule.status() == ScheduleController.NO_ROUTE,
            "switch_disabled".equals(blocked),
            "held_by_another_consist".equals(blocked),
            schedule.status() == ScheduleController.SIGNAL_WAIT,
            0,
            false,
            atStop,
            emergency,
            atStop));
  }


  /**
   * Signed distance to the stop, from the room the physics step last used.
   *
   * <p>Positive means the stop is still ahead. A train that overshot reports negative, because "3 blocks to go"
   * and "3 blocks past the platform" are opposite problems.
   */
  double resolveOffset() {
    if (!(getLevel() instanceof net.minecraft.server.level.ServerLevel server) || !bound) return 0;
    var body = DriverControls.body(this);
    if (body == null) return 0;
    double room = ApproachRooms.room(server, body.getUniqueId());
    if (Double.isNaN(room)) return 0;
    return schedule.status() == ScheduleController.OVERSHOOT
        ? StopOffset.past(room)
        : StopOffset.ahead(room);
  }



  /** One line per second, so a train that will not move has a stated reason in the log. */
  private void reportStatus() {
    if (!TrainConfig.DETAILED_LOGGING.get() || getLevel() == null) return;
    long now = getLevel().getGameTime();
    if (now < nextStatusLog) return;
    nextStatusLog = now + 20;
    // Once per second is enough to catch a disagreement between the wait flag and the stage; the flag is a fold
    // candidate only while they agree.
    schedule.auditWaitFlag();
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[ConsoleStatus] console={} reason={} intent={} stage={} bound={}",
            getBlockPos(),
            statusReason,
            intent(),
            schedule.stage(),
            bound);
  }


  /**
   * Goggles answer "what is this", in the same words the docking connector uses.
   *
   * <p>A block with goggles on says what it is, what it needs and what state it is in; the live reason a train is not
   * moving belongs to the train, not to the block being inspected.
   */
  @Override
  public boolean addToGoggleTooltip(
      java.util.List<net.minecraft.network.chat.Component> tooltip, boolean sneaking) {
    // VisualBound is synchronized by the server; DriverControls.body only resolves ServerSubLevel.
    for (var line : GoggleText.console(bound, schedule.present(), statusReason, sneaking,
        direction, notch, trainName))
      GoggleText.add(tooltip, line);
    return true;
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = saveWithoutMetadata(registries);
    tag.putBoolean("VisualActive", active);
    tag.putInt("VisualNotch", notch);
    tag.putBoolean("VisualEmergency", emergency);
    // Resolved here as well, because a console nobody has touched may never have run the sync path
    // that refreshes it, and the client would then draw no lamps at all.
    bound = DriverControls.body(this) != null;
    tag.putBoolean("VisualBound", bound);
    statusReason = resolveStatus();
    tag.putString("VisualStatus", statusReason.name());
    tag.putBoolean("VisualAligning", aligning);
    stopOffset = resolveOffset();
    tag.putDouble("VisualOffset", stopOffset);
    return tag;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("Direction", direction);
    tag.putString("TrainName", trainName);
    if (trainOwner.known()) {
      tag.putUUID("PlacedBy", trainOwner.id());
      tag.putString("PlacedByName", trainOwner.name());
    }
    tag.putIntArray("GearSpeeds", gearSpeeds());
    tag.putUUID("ConsoleId", consoleId);
    tag.putLong("PlacementOrder", placementOrder);
    tag.putBoolean("Emergency", emergency);
    // The lever is a setting the operator left, not the takeover session: an unloaded carriage
    // must come back in the gear it was left in, or the driver has to re-dial it after every stop.
    tag.putInt("Notch", notch);
    schedule.save(tag, registries);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {

      SyncWatch.note(DriverConsoleEntity.class);
    super.loadAdditional(tag, registries);
    if (tag.hasUUID("ConsoleId")) consoleId = tag.getUUID("ConsoleId");
    placementOrder = Math.max(0, tag.getLong("PlacementOrder"));
    trainName = TrainName.normalize(tag.getString("TrainName"));
    trainOwner =
        new TrainOwner(
            tag.hasUUID("PlacedBy") ? tag.getUUID("PlacedBy") : null,
            tag.getString("PlacedByName"));
    schedule.load(tag, registries);
    direction = tag.contains("Direction") ? Math.clamp(tag.getInt("Direction"), -1, 1) : 1;
    int[] gears = tag.getIntArray("GearSpeeds");
    gearSpeeds = SpeedSetting.validGears(SpeedSetting.MAX, gears) ? gears : null;
    if (tag.contains("VisualNotch")) {
      active = tag.getBoolean("VisualActive");
      notch =
          Math.clamp(tag.getInt("VisualNotch"), -DriverModel.BRAKE_NOTCHES, DriverModel.MAX_NOTCH);
      emergency = tag.getBoolean("VisualEmergency");
      bound = tag.getBoolean("VisualBound");
    statusReason =
        tag.contains("VisualStatus")
            ? ConsoleStatus.valueOf(tag.getString("VisualStatus"))
            : ConsoleStatus.NO_BODY;
    stopOffset = tag.getDouble("VisualOffset");
    aligning = tag.getBoolean("VisualAligning");
    } else {
      // Disk: the takeover session is never restored, but the lever setting is.
      active = false;
      enabled = false;
      emergency = tag.getBoolean("Emergency");
      notch =
          tag.contains("Notch")
              ? Math.clamp(tag.getInt("Notch"), -DriverModel.BRAKE_NOTCHES, DriverModel.MAX_NOTCH)
              : -4;
    } // Manual takeover is never restored from disk.
  }

  @Override
  public void setRemoved() {
    if (level instanceof ServerLevel server) DriverControls.unloaded(server, this);
    super.setRemoved();
  }
}
