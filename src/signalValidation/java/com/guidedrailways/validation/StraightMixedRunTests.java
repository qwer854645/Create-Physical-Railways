package com.guidedrailways.validation;

import com.guidedrailways.*;
import com.simibubi.create.*;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;

/** Real track, native station assembly and Sable placement. No injected occupancy or speed. */
@GameTestHolder("guidedrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class StraightMixedRunTests {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger("guidedrailways.moving-validation");

  private static java.lang.reflect.Field member(Class<?> type, String name) throws Exception {
    for (var current = type; current != null; current = current.getSuperclass())
      try {
        var f = current.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
      }
    throw new NoSuchFieldException(name);
  }

  private static Object get(Object object, String name) throws Exception {
    return member(object.getClass(), name).get(object);
  }

  private static void set(Object object, String name, Object value) throws Exception {
    member(object.getClass(), name).set(object, value);
  }

  private static Object call(Object object, String name, Class<?>[] types, Object... args)
      throws Exception {
    var method = object.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(object, args);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static BlockState facing(BlockState state, Direction direction) {
    for (var property : state.getProperties())
      if (property.getName().equals("facing") && property.getPossibleValues().contains(direction))
        return state.setValue((Property) property, direction);
    return state;
  }

  @GameTest(
      batch = "moving",
      template = "empty",
      templateNamespace = "guidedrailways_signal_validation",
      timeoutTicks = 1600)
  public static void nativeLeader(GameTestHelper helper) {
    new Run(helper, true).start();
  }

  @GameTest(
      batch = "moving",
      template = "empty",
      templateNamespace = "guidedrailways_signal_validation",
      timeoutTicks = 1600)
  public static void physicalLeader(GameTestHelper helper) {
    new Run(helper, false).start();
  }

  private static final class Run {
    final GameTestHelper helper;
    final ServerLevel level;
    final boolean nativeLeader;
    final BlockPos base;
    final List<SignalBlockEntity> signals = new ArrayList<>();
    StationBlockEntity assembly, destination;
    Train nativeTrain, leaderIdentity;
    UUID occupiedSection;
    Object unit;
    DriverConsoleEntity driver;
    ServerSubLevel carrier;
    int tick, stoppedTicks;
    boolean moved, released, tailCleared;
    double minimumGap = Double.POSITIVE_INFINITY;
    final Set<Long> forcedChunks = new HashSet<>();
    double initialFollower, releaseLeader;

    Run(GameTestHelper helper, boolean nativeLeader) {
      this.helper = helper;
      this.level = helper.getLevel();
      this.nativeLeader = nativeLeader;
      // Rapier uses float positions: random GameTest origins millions of blocks away quantise
      // small integration steps to zero. Both scenes stay near zero in the isolated test world.
      base = new BlockPos(nativeLeader ? 1024 : 1536, 80, 0);
    }

    void later(int ticks, Runnable action) {
      helper.runAfterDelay(
          ticks,
          () -> {
            try {
              action.run();
            } catch (Throwable error) {
              LOG.error("MOVING_FIXTURE_FAILED tick={}", tick, error);
              cleanup();
              helper.fail(error.toString());
            }
          });
    }

    void start() {
      try {
        var track = AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, TrackShape.ZO);
        for (int z = 0; z <= 180; z++) {
          var pos = base.offset(0, 0, z);
          var chunk = new net.minecraft.world.level.ChunkPos(pos);
          if (forcedChunks.add(chunk.toLong())) level.setChunkForced(chunk.x, chunk.z, true);
          level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(pos, track, 3);
        }
        assembly = station(nativeLeader ? 80 : 120);
        destination = station(20);
        for (int z : new int[] {60, 100, 140}) {
          var pos = base.offset(2, 0, z);
          level.setBlock(pos, AllBlocks.TRACK_SIGNAL.getDefaultState(), 3);
          var signal = (SignalBlockEntity) level.getBlockEntity(pos);
          bind(signal.edgePoint, base.offset(0, 0, z), pos);
          signals.add(signal);
        }
        later(
            80,
            () -> {
              try {
                assemble();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
      } catch (Exception e) {
        LOG.error("MOVING_FIXTURE_SETUP_FAILED", e);
        cleanup();
        helper.fail(e.toString());
      }
    }

    StationBlockEntity station(int z) throws Exception {
      var pos = base.offset(2, 0, z);
      level.setBlock(pos, AllBlocks.TRACK_STATION.getDefaultState(), 3);
      var station = (StationBlockEntity) level.getBlockEntity(pos);
      bind(station.edgePoint, base.offset(0, 0, z), pos);
      return station;
    }

    void bind(Object behaviour, BlockPos track, BlockPos block) throws Exception {
      set(behaviour, "targetTrack", track.subtract(block));
      set(behaviour, "targetDirection", Direction.AxisDirection.POSITIVE);
    }

    void assemble() throws Exception {
      helper.assertTrue(
          assembly.getStation() != null && destination.getStation() != null,
          "stations attached to real track graph");
      destination.updateName("Destination");
      helper.assertTrue(
          signals.stream().allMatch(s -> s.getSignal() != null),
          "signals attached to real track graph");
      var direction = assembly.getAssemblyDirection();
      helper.assertTrue(direction != null, "native assembly direction resolved");
      helper.assertTrue(assembly.tryEnterAssemblyMode(), "native assembly mode entered");
      var bogey = base.offset(0, 0, nativeLeader ? 80 : 120).relative(direction, 1).above();
      level.setBlock(
          bogey,
          AllBlocks.SMALL_BOGEY
              .getDefaultState()
              .setValue(
                  com.simibubi.create.content.trains.bogey.AbstractBogeyBlock.AXIS,
                  direction.getAxis()),
          3);
      var seat = bogey.above(2);
      var control = seat.relative(direction.getOpposite());
      level.setBlock(seat.below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
      level.setBlock(control.below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
      level.setBlock(
          seat, AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.RED).getDefaultState(), 3);
      var controls = facing(AllBlocks.TRAIN_CONTROLS.getDefaultState(), direction);
      if (controls.hasProperty(BlockStateProperties.ATTACH_FACE))
        controls = controls.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
      level.setBlock(control, controls, 3);
      level.addFreshEntity(
          new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(
              level,
              new net.minecraft.world.phys.AABB(
                  bogey.getX(),
                  bogey.getY(),
                  Math.min(bogey.getZ(), control.getZ()),
                  bogey.getX() + 1,
                  seat.getY() + 1,
                  Math.max(bogey.getZ(), control.getZ()) + 1)));
      var conductor = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
      conductor.setPos(seat.getX() + .5, seat.getY() + .5, seat.getZ() + .5);
      level.addFreshEntity(conductor);
      com.simibubi.create.content.contraptions.actors.seat.SeatBlock.sitDown(
          level, seat, conductor);
      assembly.refreshAssemblyInfo();
      var before = Set.copyOf(Create.RAILWAYS.trains.keySet());
      assembly.assemble(UUID.randomUUID());
      nativeTrain =
          Create.RAILWAYS.trains.values().stream()
              .filter(t -> !before.contains(t.id) && !(t instanceof PhysicalTrainProxy))
              .findFirst()
              .orElse(null);
      helper.assertTrue(
          nativeTrain != null,
          "native train assembled: "
              + (get(assembly, "lastException")
                      instanceof com.simibubi.create.content.contraptions.AssemblyException error
                  ? error.component.getString()
                  : "no exception"));
      LOG.info(
          "MOVING_NATIVE_ASSEMBLED id={} direction={} carriages={}",
          nativeTrain.id,
          direction,
          nativeTrain.carriages.size());
      assembly.exitAssemblyMode();
      later(
          30,
          () -> {
            try {
              physical();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    }

    void physical() throws Exception {
      var player = FakePlayerFactory.getMinecraft(level);
      player.getAbilities().instabuild = true;
      player.setPos(base.getX() + .5, base.getY() + 2, base.getZ() + (nativeLeader ? 130.5 : 80.5));
      player.setYRot(180);
      player.setXRot(90);
      player.setItemInHand(
          InteractionHand.MAIN_HAND,
          new net.minecraft.world.item.ItemStack(Content.BOGIE_ITEM.get()));
      var unitsMethod = VehiclePhysics.class.getDeclaredMethod("cargoUnits", ServerLevel.class);
      unitsMethod.setAccessible(true);
      var before = new HashSet<>((Collection<?>) unitsMethod.invoke(null, level));
      var placement = Class.forName("com.guidedrailways.VehiclePlacement");
      var method = placement.getDeclaredMethod("placeAimed", ServerPlayer.class);
      method.setAccessible(true);
      method.invoke(null, player);
      var units =
          ((Collection<?>) unitsMethod.invoke(null, level))
              .stream().filter(u -> !before.contains(u)).toList();
      helper.assertTrue(units.size() == 1, "one real physical bogey placed");
      unit = units.iterator().next();
      carrier = (ServerSubLevel) get(unit, "carrier");
      var mount = (net.minecraft.world.level.block.entity.BlockEntity) get(unit, "mount");
      var pos = mount.getBlockPos();
      level.setBlock(
          pos.above(), facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.DOWN), 3);
      var motor = level.getBlockEntity(pos.above());
      ((com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour)
              get(motor, "generatedSpeed"))
          .setValue(32);
      level.setBlock(pos.east(), Content.DRIVER.get().defaultBlockState(), 3);
      driver = (DriverConsoleEntity) level.getBlockEntity(pos.east());
      call(
          driver,
          "placed",
          new Class<?>[] {ServerLevel.class, net.minecraft.world.entity.player.Player.class},
          level,
          player);
      LOG.info(
          "MOVING_PHYSICAL_ASSEMBLED body={} mount={} driver={}",
          carrier.getUniqueId(),
          pos,
          driver.getBlockPos());
      later(
          50,
          () -> {
            try {
              begin();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    }

    Schedule schedule() {
      var instruction = new DestinationInstruction();
      instruction.getData().putString("Text", "Destination");
      return new Schedule(List.of(new ScheduleEntry(instruction, List.of())), false, 0);
    }

    double nativeZ() {
      return nativeTrain.getPositionInDimension(level.dimension()).orElseThrow().getZ()
          - base.getZ();
    }

    double physicalZ() {
      return dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level)
              .getPipeline()
              .readPose(carrier, new dev.ryanhcode.sable.companion.math.Pose3d())
              .position()
              .z()
          - base.getZ();
    }

    void begin() throws Exception {
      helper.assertTrue(nativeTrain.hasForwardConductor(), "real native conductor captured");
      var physicalIdentity =
          Create.RAILWAYS.trains.values().stream()
              .filter(t -> t instanceof PhysicalTrainProxy && t.graph == nativeTrain.graph)
              .findFirst()
              .orElseThrow();
      leaderIdentity = nativeLeader ? nativeTrain : physicalIdentity;
      var boundary = signals.get(1).getSignal();
      var a = boundary.edgeLocation.getFirst();
      var b = boundary.edgeLocation.getSecond();
      var downstream = nativeTrain.graph.locateNode(a.getZ() < b.getZ() ? a : b);
      occupiedSection = boundary.getGroup(downstream);
      helper.assertTrue(
          Create.RAILWAYS.signalEdgeGroups.get(occupiedSection).trains.contains(leaderIdentity),
          "real leading train occupies the following section");
      initialFollower = nativeLeader ? physicalZ() : nativeZ();
      if (nativeLeader) startPhysical();
      else startNative();
      observe();
    }

    void startNative() {
      nativeTrain.runtime.setSchedule(schedule(), false);
      nativeTrain.runtime.paused = false;
    }

    void startPhysical() throws Exception {
      var player = FakePlayerFactory.getMinecraft(level);
      var world =
          VehiclePhysics.class.getDeclaredMethod(
              "world", ServerSubLevel.class, org.joml.Vector3dc.class);
      world.setAccessible(true);
      var point =
          (Vector3d)
              world.invoke(
                  null,
                  carrier,
                  new Vector3d(
                      driver.getBlockPos().getX() + .5,
                      driver.getBlockPos().getY() + .5,
                      driver.getBlockPos().getZ() + .5));
      player.setPos(point.x, point.y, point.z);
      var item = AllItems.SCHEDULE.asStack();
      item.set(AllDataComponents.TRAIN_SCHEDULE, schedule().write(level.registryAccess()));
      helper.assertTrue(
          (boolean)
              call(
                  get(driver, "schedule"),
                  "install",
                  new Class<?>[] {
                    net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.item.ItemStack.class
                  },
                  player,
                  item),
          "physical schedule installed through normal entry point");
    }

    void observe() throws Exception {
      tick++;
      double follower = nativeLeader ? physicalZ() : nativeZ(),
          leader = nativeLeader ? nativeZ() : physicalZ();
      double speed =
          nativeLeader ? ((Number) get(driver, "actual")).doubleValue() : nativeTrain.speed * 20;
      moved |= initialFollower - follower > 3;
      if (tick % 20 == 0)
        LOG.info(
            "MOVING_SAMPLE nativeLeader={} tick={} nativeZ={} physicalZ={} physicalSpeed={} nativeSpeed={} signal={} status={} released={}",
            nativeLeader,
            tick,
            nativeZ(),
            physicalZ(),
            get(driver, "actual"),
            nativeTrain.speed * 20,
            signals.get(1).getState(),
            get(get(driver, "schedule"), "status"),
            released);
      helper.assertTrue(!nativeTrain.derailed, "native train remains on track");
      helper.assertTrue(!(boolean) get(unit, "detached"), "physical train remains on track");
      minimumGap = Math.min(minimumGap, follower - leader);
      boolean occupied =
          Create.RAILWAYS.signalEdgeGroups.get(occupiedSection).trains.contains(leaderIdentity);
      if (released && !occupied && !tailCleared) {
        tailCleared = true;
        LOG.info(
            "MOVING_TAIL_CLEARED nativeLeader={} tick={} leader={} follower={}",
            nativeLeader,
            tick,
            leader,
            follower);
      }
      if (!tailCleared)
        helper.assertTrue(
            follower > 100, "follower cannot cross signal before leader tail clears section");
      helper.assertTrue(follower - leader > 5, "vehicles remain separated");
      if (!released && moved && follower < 115 && follower > 100 && Math.abs(speed) < .1)
        stoppedTicks++;
      else if (!released) stoppedTicks = 0;
      if (!released && stoppedTicks >= 20) {
        helper.assertTrue(
            signals.get(1).getState() == SignalBlockEntity.SignalState.RED,
            "following train stops at red");
        helper.assertTrue(occupied, "red stop is caused by the actual leading train");
        LOG.info(
            "MOVING_STOP_CONFIRMED nativeLeader={} tick={} position={} speed={} stableTicks={}",
            nativeLeader,
            tick,
            follower,
            speed,
            stoppedTicks);
        releaseLeader = leader;
        if (nativeLeader) startNative();
        else startPhysical();
        released = true;
        LOG.info("MOVING_LEADER_RELEASED tick={}", tick);
      }
      if (released && tailCleared && releaseLeader - leader > 10 && follower < 96) {
        LOG.info(
            "MOVING_MIXED_PASS nativeLeader={} tick={} leader={} follower={} minimumGap={}",
            nativeLeader,
            tick,
            leader,
            follower,
            minimumGap);
        cleanup();
        helper.succeed();
        return;
      }
      helper.assertTrue(tick < 1300, "moving mixed scenario completed within deadline");
      later(
          1,
          () -> {
            try {
              observe();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    }

    void cleanup() {
      if (nativeTrain != null) {
        nativeTrain.carriages.forEach(
            c -> c.forEachPresentEntity(net.minecraft.world.entity.Entity::discard));
        Create.RAILWAYS.trains.remove(nativeTrain.id);
        for (var group : Create.RAILWAYS.signalEdgeGroups.values())
          group.trains.remove(nativeTrain);
      }
      if (unit != null)
        try {
          var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
          var bogie = (ServerSubLevel) get(unit, "bogie");
          if (!bogie.isRemoved())
            container.removeSubLevel(
                bogie, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
          if (!carrier.isRemoved())
            container.removeSubLevel(
                carrier, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        } catch (Exception error) {
          LOG.error("MOVING_CLEANUP_FAILED", error);
        }
      for (long key : forcedChunks) {
        var chunk = new net.minecraft.world.level.ChunkPos(key);
        level.setChunkForced(chunk.x, chunk.z, false);
      }
      forcedChunks.clear();
    }
  }
}
