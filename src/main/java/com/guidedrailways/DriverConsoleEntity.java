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

public final class DriverConsoleEntity extends BlockEntity implements MenuProvider {
  final ScheduleController schedule = new ScheduleController(this);
  java.util.UUID consoleId = java.util.UUID.randomUUID();
  long placementOrder;
  java.util.UUID operator;
  int steer;
  long steerAt;
  boolean active, enabled, emergency, observing;
  int direction = 1, notch = -4;
  private long pressedUntil;
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
    setChanged();
    if (level instanceof ServerLevel server && !isRemoved()) {
      syncDisplay();
      sendVisual(server);
      DriverNetwork.sync(this, active);
    }
  }

  /** The display index lives in the block state, so the client only has to pick a model. */
  private void syncDisplay() {
    var state = getBlockState();
    if (!state.hasProperty(DriverConsoleBlock.SCREEN)) return;
    int want = ConsoleDisplay.index(emergency, active, observing, notch);
    if (state.getValue(DriverConsoleBlock.SCREEN) == want) return;
    level.setBlock(
        worldPosition, state.setValue(DriverConsoleBlock.SCREEN, want), Block.UPDATE_CLIENTS);
  }

  /** Ticks the button stays visibly pressed. */
  private static final long PRESS_TICKS = 6;

  /** Depresses the emergency button for a moment, so pressing it is visible to everyone nearby. */
  void press() {
    if (level == null) return;
    pressedUntil = level.getGameTime() + PRESS_TICKS;
    applyPressed(true);
  }

  /** Releases the button once its press animation is over. */
  void clearPressIfDue() {
    if (pressedUntil == 0 || level == null || level.getGameTime() < pressedUntil) return;
    pressedUntil = 0;
    applyPressed(false);
  }

  private void applyPressed(boolean pressed) {
    var state = getBlockState();
    if (!state.hasProperty(DriverConsoleBlock.PRESSED)) return;
    if (state.getValue(DriverConsoleBlock.PRESSED) == pressed) return;
    level.setBlock(
        worldPosition, state.setValue(DriverConsoleBlock.PRESSED, pressed), Block.UPDATE_CLIENTS);
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
      } else if (command == 1) notch = DriverModel.step(notch, -1);
      else if (command == 2) notch = DriverModel.step(notch, 1);
      else if (command == 6) notch = 0;
      else if (command == 7) notch = -4;
      else if (DriverInputPolicy.notchCommand(command))
        notch = command - DriverInputPolicy.NOTCH_BASE;
      else return;
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

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = saveWithoutMetadata(registries);
    tag.putBoolean("VisualActive", active);
    tag.putInt("VisualNotch", notch);
    tag.putBoolean("VisualEmergency", emergency);
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
