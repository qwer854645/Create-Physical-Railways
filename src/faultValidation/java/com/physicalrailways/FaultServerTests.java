package com.physicalrailways;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.*;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;

/** Real Sable bodies and normal placement/control; fixtures never enter the release artifact. */
@GameTestHolder("physicalrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class FaultServerTests {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger("physicalrailways.fault-validation");

  static Object get(Object owner, String name) throws Exception {
    var f = owner.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(owner);
  }

  static void set(Object owner, String name, Object value) throws Exception {
    var f = owner.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(owner, value);
  }

  @GameTest(
      batch = "fault",
      template = "empty",
      templateNamespace = "physicalrailways_signal_validation",
      timeoutTicks = 2400)
  public static void movingConsistFaultAndReload(GameTestHelper helper) {
    new FaultRun(helper).start();
  }

  @GameTest(
      batch = "readiness_scale",
      template = "empty",
      templateNamespace = "physicalrailways_signal_validation",
      timeoutTicks = 2400)
  public static void recoveryCheckScaling(GameTestHelper helper) {
    new ScaleRun(helper).start();
  }

  static class Scene {
    final GameTestHelper helper;
    final ServerLevel level;
    final List<Car> cars = new ArrayList<>();
    final Set<Long> forced = new HashSet<>();

    Scene(GameTestHelper helper) {
      this.helper = helper;
      level = helper.getLevel();
    }

    interface Step {
      void run() throws Exception;
    }

    void later(int delay, Step step) {
      helper.runAfterDelay(
          delay,
          () -> {
            try {
              step.run();
            } catch (Throwable error) {
              LOG.error("FAULT_FIXTURE_FAILED gameTick={}", level.getGameTime(), error);
              cleanup();
              helper.fail(error.toString());
            }
          });
    }

    void track(int x, int length) {
      for (int z = 0; z <= length; z++) {
        var p = new BlockPos(x, 80, z);
        var chunk = new ChunkPos(p);
        if (forced.add(chunk.toLong())) level.setChunkForced(chunk.x, chunk.z, true);
        level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(
            p, AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, TrackShape.ZO), 3);
      }
    }

    StationBlockEntity station(int x, String name) throws Exception {
      var p = new BlockPos(x + 2, 80, 20);
      level.setBlock(p, AllBlocks.TRACK_STATION.getDefaultState(), 3);
      var station = (StationBlockEntity) level.getBlockEntity(p);
      set(station.edgePoint, "targetTrack", new BlockPos(-2, 0, 0));
      set(station.edgePoint, "targetDirection", Direction.AxisDirection.POSITIVE);
      return station;
    }

    Car car(int x, int z, boolean controlled) throws Exception {
      var player = FakePlayerFactory.getMinecraft(level);
      player.getAbilities().instabuild = true;
      player.setPos(x + .5, 82, z + .5);
      player.setYRot(180);
      player.setXRot(90);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.BOGIE_ITEM.get()));
      var before = new HashSet<>(VehiclePhysics.cargoUnits(level));
      VehiclePlacement.placeAimed(player);
      var units =
          VehiclePhysics.cargoUnits(level).stream().filter(u -> !before.contains(u)).toList();
      helper.assertTrue(units.size() == 1, "normal placement produces one physical bogie");
      var car = new Car(units.getFirst());
      cars.add(car);
      var p = car.unit.mount.getBlockPos();
      if (controlled) {
        level.setBlock(
            p.above(),
            AllBlocks.CREATIVE_MOTOR
                .getDefaultState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN),
            3);
        ((ScrollValueBehaviour) get(level.getBlockEntity(p.above()), "generatedSpeed"))
            .setValue(32);
        level.setBlock(p.east(), Content.DRIVER.get().defaultBlockState(), 3);
        car.driver = (DriverConsoleEntity) level.getBlockEntity(p.east());
        car.driver.placed(level, player);
      }
      for (int sign : new int[] {-1, 1}) {
        level.setBlock(p.offset(0, 0, sign), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        var q = p.offset(0, 0, sign * 2);
        level.setBlock(
            q,
            Content.COUPLER
                .get()
                .defaultBlockState()
                .setValue(CouplerBlock.FACING, sign < 0 ? Direction.NORTH : Direction.SOUTH),
            3);
        car.ends.add(((CouplerEntity) level.getBlockEntity(q)).endpoint);
      }
      level.setBlock(p.west(), Blocks.CHEST.defaultBlockState(), 3);
      car.chest = (ChestBlockEntity) level.getBlockEntity(p.west());
      car.chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
      return car;
    }

    void connect(Car a, Car b) throws Exception {
      CouplerEndpoint first = null, second = null;
      double distance = Double.POSITIVE_INFINITY;
      for (var x : a.ends)
        for (var y : b.ends) {
          double d =
              VehiclePhysics.world(a.unit.carrier, CouplerCoordinates.head(x))
                  .distance(VehiclePhysics.world(b.unit.carrier, CouplerCoordinates.head(y)));
          if (d < distance) {
            distance = d;
            first = x;
            second = y;
          }
        }
      LOG.info("FAULT_COUPLE_SETUP gap={}", distance);
      var method =
          CouplerConnections.class.getDeclaredMethod(
              "connect", ServerLevel.class, CouplerEndpoint.class, CouplerEndpoint.class);
      method.setAccessible(true);
      method.invoke(null, level, first, second);
    }

    ServerPlayer operator(Car car) {
      var player = FakePlayerFactory.getMinecraft(level);
      var p = car.driver.getBlockPos();
      var world =
          VehiclePhysics.world(
              car.unit.carrier, new Vector3d(p.getX() + .5, p.getY() + .5, p.getZ() + .5));
      player.setPos(world.x, world.y, world.z);
      return player;
    }

    void schedule(Car car, String target) {
      var instruction = new DestinationInstruction();
      instruction.getData().putString("Text", target);
      var schedule = new Schedule(List.of(new ScheduleEntry(instruction, List.of())), false, 0);
      var item = AllItems.SCHEDULE.asStack();
      item.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(level.registryAccess()));
      helper.assertTrue(
          car.driver.schedule.install(operator(car), item),
          "schedule installs through player entry point");
    }

    double z(Car car) {
      return SubLevelPhysicsSystem.get(level)
          .getPipeline()
          .readPose(car.unit.carrier, new Pose3d())
          .position()
          .z;
    }

    double speed(Car car) {
      return SubLevelPhysicsSystem.get(level)
          .getPipeline()
          .getLinearVelocity(car.unit.carrier, new Vector3d())
          .length();
    }

    void cleanup() {
      var container = SubLevelContainer.getContainer(level);
      for (var car : cars)
        for (var id : List.of(car.unit.carrier.getUniqueId(), car.unit.bogie.getUniqueId())) {
          var body = container.getSubLevel(id);
          if (body != null && !body.isRemoved())
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
      for (long key : forced) {
        var chunk = new ChunkPos(key);
        level.setChunkForced(chunk.x, chunk.z, false);
      }
      forced.clear();
    }
  }

  static final class Car {
    VehiclePhysics.Unit unit;
    DriverConsoleEntity driver;
    ChestBlockEntity chest;
    final List<CouplerEndpoint> ends = new ArrayList<>();

    Car(VehiclePhysics.Unit unit) {
      this.unit = unit;
    }
  }

  static final class FaultRun extends Scene {
    Car a, tail, other;
    StationBlockEntity destination, otherDestination;
    Schedule good;
    double otherAtFault, faultZ, stopSpeed;
    long faultAt, unloadAt;
    int ticks, stopped;
    SubLevelData unloaded;
    final Properties saved = new Properties();
    int restartWait;

    FaultRun(GameTestHelper helper) {
      super(helper);
    }

    void start() {
      if (Boolean.getBoolean("physicalrailways.validationRestart")) {
        later(
            1,
            () -> {
              try (var input = Files.newInputStream(Path.of("fault-restart.properties"))) {
                saved.load(input);
              }
              for (int z = 0; z <= 160; z += 16) {
                var chunk = new ChunkPos(new BlockPos(2048, 80, z));
                if (forced.add(chunk.toLong())) {
                  level.getChunk(chunk.x, chunk.z);
                  level.setChunkForced(chunk.x, chunk.z, true);
                }
              }
              later(1, this::awaitRestart);
            });
        return;
      }
      later(
          1,
          () -> {
            track(2048, 160);
            track(2064, 160);
            destination = station(2048, "Fault Destination");
            otherDestination = station(2064, "Other Destination");
            later(
                80,
                () -> {
                  helper.assertTrue(
                      destination.getStation() != null && otherDestination.getStation() != null,
                      "real stations registered");
                  destination.updateName("Fault Destination");
                  otherDestination.updateName("Other Destination");
                  a = car(2048, 120, true);
                  tail = car(2048, 126, false);
                  other = car(2064, 120, true);
                  later(
                      60,
                      () -> {
                        connect(a, tail);
                        later(
                            40,
                            () -> {
                              var group =
                                  TrainConsists.get(level).group(a.unit.carrier.getUniqueId());
                              helper.assertTrue(
                                  group != null && group.units().size() == 2,
                                  "two physical carriages form one real consist");
                              schedule(a, "Fault Destination");
                              schedule(other, "Other Destination");
                              good = (Schedule) get(a.driver.schedule, "program");
                              later(1, this::awaitMotion);
                            });
                      });
                });
          });
    }

    void awaitMotion() throws Exception {
      helper.assertTrue(++ticks < 500, "both consists begin moving");
      if (speed(a) < 1 || speed(other) < 1 || z(a) > 116 || z(other) > 116) {
        later(1, this::awaitMotion);
        return;
      }
      otherAtFault = z(other);
      faultZ = z(a);
      faultAt = level.getGameTime();
      // Corrupt only the cached instruction type; the real automatic-control boundary must catch
      // it.
      var invalid = new ChangeTitleInstruction() {};
      set(
          a.driver.schedule,
          "program",
          new Schedule(List.of(new ScheduleEntry(invalid, List.of())), false, 0));
      later(
          2,
          () -> {
            helper.assertTrue(
                a.driver.schedule.status() == ScheduleController.RUNTIME_FAULT,
                "real instruction exception pauses the schedule");
            helper.assertTrue(
                !a.driver.schedule.running() && !a.driver.enabled,
                "fault cannot leave a driver or schedule powering the train");
            helper.assertTrue(
                TrainFaults.blocked(level, a.unit.carrier.getUniqueId())
                    && TrainFaults.blocked(level, tail.unit.carrier.getUniqueId()),
                "fault holds both carriages");
            helper.assertTrue(
                !TrainFaults.blocked(level, other.unit.carrier.getUniqueId()),
                "other consist stays operational");
            helper.assertTrue(
                a.unit.command == null || a.unit.command.hold(),
                "front carriage has no positive traction command");
            helper.assertTrue(
                tail.unit.command == null || tail.unit.command.hold(),
                "tail carriage has no positive traction command");
            set(a.driver.schedule, "program", good);
            later(1, this::awaitStop);
          });
    }

    void awaitStop() throws Exception {
      long elapsed = level.getGameTime() - faultAt;
      helper.assertTrue(elapsed < 180, "faulted consist stops within nine seconds");
      stopSpeed = Math.max(speed(a), speed(tail));
      if (stopSpeed < .1) stopped++;
      else stopped = 0;
      helper.assertTrue(
          !a.driver.schedule.running(), "stable readiness must not restart a faulted schedule");
      helper.assertTrue(
          !a.unit.detached && !tail.unit.detached, "fault braking retains rail guidance");
      if (stopped < 20 || elapsed < 60) {
        later(1, this::awaitStop);
        return;
      }
      helper.assertTrue(
          otherAtFault - z(other) > 4, "unrelated train continues travelling during fault braking");
      helper.assertTrue(
          a.driver.schedule.present() && a.driver.schedule.entry() == 0,
          "fault retains schedule and progress");
      helper.assertTrue(
          a.chest.getItem(0).getCount() == 17 && tail.chest.getItem(0).getCount() == 17,
          "fault retains both cargo inventories");
      helper.assertTrue(
          CouplerSavedData.get(level).links.all().size() >= 1
              && CouplerConnections.cargoComplete(
                  level, TrainConsists.get(level).group(a.unit.carrier.getUniqueId()).members()),
          "saved coupling and real joint survive fault");
      LOG.info(
          "FAULT_MOVING_PASS elapsedTicks={} stopSpeed={} stopDistance={} unrelatedDistance={}",
          elapsed,
          stopSpeed,
          faultZ - z(a),
          otherAtFault - z(other));
      helper.assertTrue(
          a.driver.schedule.action(operator(a), 30), "player can resume after recovery");
      later(25, this::unloadTail);
    }

    void unloadTail() {
      unloaded = SubLevelSerializer.toData(tail.unit.bogie, List.of());
      SubLevelContainer.getContainer(level)
          .removeSubLevel(tail.unit.bogie, SubLevelRemovalReason.UNLOADED);
      unloadAt = level.getGameTime();
      later(
          10,
          () -> {
            var container = SubLevelContainer.getContainer(level);
            LOG.info(
                "FAULT_UNLOAD_SAMPLE nativePresent={} activeAxles={} held={}",
                container.getSubLevel(unloaded.uuid()) != null,
                TrainConsists.get(level).group(a.unit.carrier.getUniqueId()).units().size(),
                TrainFaults.blocked(level, a.unit.carrier.getUniqueId()));
            helper.assertTrue(
                container.getSubLevel(unloaded.uuid()) == null,
                "native axle stays unloaded before explicit restore");
            helper.assertTrue(
                TrainFaults.blocked(level, a.unit.carrier.getUniqueId()),
                "missing saved axle holds the whole consist");
            helper.assertTrue(
                a.driver.schedule.running(), "temporary loading preserves automatic intent");
            SubLevelSerializer.fullyLoad(level, unloaded);
            later(1, this::awaitReload);
          });
    }

    void awaitReload() {
      long elapsed = level.getGameTime() - unloadAt;
      helper.assertTrue(elapsed < 240, "native axle and train recover within deadline");
      if (TrainFaults.blocked(level, a.unit.carrier.getUniqueId())) {
        later(1, this::awaitReload);
        return;
      }
      tail.unit = VehiclePhysics.unit(level, unloaded.uuid());
      helper.assertTrue(
          tail.unit != null && !tail.unit.detached,
          "reloaded axle rebuilds ownership and guidance");
      helper.assertTrue(elapsed >= 29, "reload does not bypass cooldown and stable readiness");
      helper.assertTrue(
          a.driver.schedule.running() && a.chest.getItem(0).getCount() == 17,
          "loading retains automatic intent and inventory");
      later(
          50,
          () -> {
            helper.assertTrue(
                speed(a) > .5 && speed(tail) > .5,
                "complete consist resumes real movement after loading");
            LOG.info(
                "FAULT_RELOAD_PASS elapsedTicks={} frontSpeed={} tailSpeed={}",
                elapsed,
                speed(a),
                speed(tail));
            a.driver.schedule.pause(ScheduleController.RUNTIME_FAULT);
            saved.setProperty("front", a.unit.carrier.getUniqueId().toString());
            saved.setProperty("tail", tail.unit.carrier.getUniqueId().toString());
            saved.setProperty("frontAxle", a.unit.bogie.getUniqueId().toString());
            saved.setProperty("tailAxle", tail.unit.bogie.getUniqueId().toString());
            saved.setProperty("driver", Long.toString(a.driver.getBlockPos().asLong()));
            var group = TrainConsists.get(level).group(a.unit.carrier.getUniqueId());
            saved.setProperty("identity", NativeTrainBridge.registered(level, group).id.toString());
            var ids = new HashSet<UUID>();
            for (var end : a.ends) ids.add(end.endpointId);
            var link =
                CouplerSavedData.get(level).links.all().stream()
                    .filter(l -> ids.contains(l.a()) || ids.contains(l.b()))
                    .findFirst()
                    .orElseThrow();
            saved.setProperty("link", link.id().toString());
            try (var output = Files.newOutputStream(Path.of("fault-restart.properties"))) {
              saved.store(output, "Isolated server restart fixture");
            }
            cars.remove(a);
            cars.remove(tail);
            cleanup();
            level.getServer().saveEverything(false, true, true);
            LOG.info(
                "FAULT_SAVE_PASS realWorldSaved=true identity={} link={}",
                saved.getProperty("identity"),
                link.id());
            helper.succeed();
          });
    }

    void awaitRestart() {
      helper.assertTrue(
          ++restartWait < 1800, "saved physical train loads after a real server restart");
      var front = VehiclePhysics.unit(level, UUID.fromString(saved.getProperty("frontAxle")));
      var rear = VehiclePhysics.unit(level, UUID.fromString(saved.getProperty("tailAxle")));
      if (front == null || rear == null || front.driver == null) {
        later(1, this::awaitRestart);
        return;
      }
      a = new Car(front);
      tail = new Car(rear);
      cars.add(a);
      cars.add(tail);
      a.driver = front.driver;
      a.chest = (ChestBlockEntity) level.getBlockEntity(front.mount.getBlockPos().west());
      tail.chest = (ChestBlockEntity) level.getBlockEntity(rear.mount.getBlockPos().west());
      later(
          80,
          () -> {
            helper.assertTrue(
                front.carrier.getUniqueId().equals(UUID.fromString(saved.getProperty("front")))
                    && rear.carrier
                        .getUniqueId()
                        .equals(UUID.fromString(saved.getProperty("tail"))),
                "restart preserves both physical carrier UUIDs");
            helper.assertTrue(
                a.driver.getBlockPos().asLong() == Long.parseLong(saved.getProperty("driver")),
                "restart restores the original driver block");
            helper.assertTrue(
                a.driver.schedule.present()
                    && !a.driver.schedule.running()
                    && !a.driver.schedule.resumePending()
                    && a.driver.schedule.entry() == 0,
                "fault-paused schedule stays paused with progress retained after restart");
            helper.assertTrue(
                a.chest.getItem(0).is(Items.DIAMOND)
                    && a.chest.getItem(0).getCount() == 17
                    && tail.chest.getItem(0).is(Items.DIAMOND)
                    && tail.chest.getItem(0).getCount() == 17,
                "restart retains both actual inventories");
            var group = TrainConsists.get(level).group(front.carrier.getUniqueId());
            helper.assertTrue(
                group != null
                    && group.units().size() == 2
                    && CouplerConnections.cargoComplete(level, group.members()),
                "restart rebuilds a complete two-car native joint consist");
            helper.assertTrue(
                CouplerSavedData.get(level).links.get(UUID.fromString(saved.getProperty("link")))
                    != null,
                "restart preserves saved coupling UUID");
            helper.assertTrue(
                NativeTrainBridge.registered(level, group)
                    .id
                    .equals(UUID.fromString(saved.getProperty("identity"))),
                "restart preserves Create query identity");
            helper.assertTrue(
                a.driver.schedule.action(operator(a), 30),
                "player can resume the saved schedule after restart");
            double start = z(a);
            later(
                100,
                () -> {
                  helper.assertTrue(
                      start - z(a) > 2 && speed(a) > .5 && speed(tail) > .5,
                      "saved consist resumes real movement after restart");
                  helper.assertTrue(
                      !a.unit.detached && !tail.unit.detached,
                      "restarted consist retains guidance");
                  LOG.info(
                      "FAULT_RESTART_PASS waitTicks={} distance={} frontSpeed={} tailSpeed={}"
                          + " identity={} link={}",
                      restartWait,
                      start - z(a),
                      speed(a),
                      speed(tail),
                      saved.getProperty("identity"),
                      saved.getProperty("link"));
                  cleanup();
                  helper.succeed();
                });
          });
    }
  }

  static final class ScaleRun extends Scene {
    int count, samples;
    final List<Long> costs = new ArrayList<>();

    ScaleRun(GameTestHelper helper) {
      super(helper);
    }

    void start() {
      later(
          1,
          () -> {
            for (int i = 0; i < 32; i++) track(3072 + i * 8, 40);
            later(80, this::grow);
          });
    }

    void grow() throws Exception {
      int target = count == 0 ? 1 : count == 1 ? 8 : 32;
      while (count < target) {
        car(3072 + count * 8, 20, false);
        count++;
      }
      costs.clear();
      samples = 0;
      later(60, this::sample);
    }

    void sample() {
      var ids = new HashSet<UUID>();
      for (var car : cars) ids.add(car.unit.carrier.getUniqueId());
      var live = TrainConsists.get(level);
      var groups =
          live.groups().stream().filter(g -> g.members().stream().anyMatch(ids::contains)).toList();
      helper.assertTrue(
          groups.size() == count, "scale sample has the expected actual independent consists");
      var byBody = new HashMap<UUID, TrainConsists.Group>();
      for (var g : groups) for (var id : g.members()) byBody.put(id, g);
      var snapshot = new TrainConsists.Snapshot(level.getGameTime(), groups, byBody, List.of());
      for (int i = 0; i < 20; i++) {
        long begin = System.nanoTime();
        TrainFaults.begin(level, snapshot);
        long duration = System.nanoTime() - begin;
        if (samples >= 5) costs.add(duration);
      }
      TrainFaults.begin(level, live); // restore the world lookup after the scoped measurement
      if (++samples < 30) {
        later(1, this::sample);
        return;
      }
      costs.sort(Long::compareTo);
      double mean = costs.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6;
      LOG.info(
          "READINESS_SCALE_PASS consists={} physicalBodies={} endpoints={} samples={} meanMs={}"
              + " medianMs={} p95Ms={} maxMs={} scope=readiness_java_only",
          count,
          count * 2,
          count * 2,
          costs.size(),
          mean,
          costs.get(costs.size() / 2) / 1e6,
          costs.get((int) (costs.size() * .95)) / 1e6,
          costs.getLast() / 1e6);
      if (count < 32) later(1, this::grow);
      else {
        cleanup();
        helper.succeed();
      }
    }
  }
}
