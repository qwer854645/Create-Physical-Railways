package com.physicalrailways;

import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.*;

/** Native API calls against normally constructed physical cars, without injecting body velocity. */
@GameTestHolder("physicalrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class NativeControlServerTests {
  @GameTest(
      batch = "native_control",
      template = "empty",
      templateNamespace = "physicalrailways_signal_validation",
      timeoutTicks = 2400)
  public static void nativeWritesDrivePhysicalConsist(GameTestHelper helper) {
    new Run(helper).start();
  }

  static final class Run extends FaultServerTests.Scene {
    FaultServerTests.Car car, tail;
    StationBlockEntity station;
    PhysicalTrainProxy proxy;
    long deadline;
    double start;
    int stable;

    Run(GameTestHelper helper) {
      super(helper);
    }

    void start() {
      later(
          1,
          () -> {
            track(2560, 160);
            station = station(2560, "Native API Destination");
            later(
                80,
                () -> {
                  helper.assertTrue(
                      station.getStation() != null, "native API scene has a real station");
                  station.updateName("Native API Destination");
                  car = car(2560, 120, true);
                  tail = car(2560, 126, false);
                  later(
                      60,
                      () -> {
                        connect(car, tail);
                        later(50, this::metadataAndProgram);
                      });
                });
          });
    }

    Schedule program() {
      var destination = new DestinationInstruction();
      destination.getData().putString("Text", "Native API Destination");
      var delay = new ScheduledDelay();
      delay.getData().putInt("Value", 30);
      return new Schedule(
          List.of(
              new ScheduleEntry(destination, List.of(List.of(delay))),
              new ScheduleEntry(destination, List.of(List.of(delay)))),
          false,
          0);
    }

    void metadataAndProgram() throws Exception {
      proxy =
          NativeTrainBridge.registered(
              level, TrainConsists.get(level).group(car.unit.carrier.getUniqueId()));
      helper.assertTrue(proxy != null, "physical consist published to the real Create registry");
      helper.assertTrue(
          proxy.hasForwardConductor() && proxy.hasBackwardConductor(),
          "physical primary controller supports native conductor capability queries");
      proxy.setCurrentStation(station.getStation());
      helper.assertTrue(
          proxy.getCurrentStation() == null,
          "native arrival callback cannot invent physical station presence");
      proxy.currentStation = station.getStation().id;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          proxy.getCurrentStation() == null,
          "direct station field mutation cannot invent arrival either");
      proxy.name = Component.literal("Native write-through");
      proxy.mapColorIndex = 7;
      var alternate =
          TrainIconType.REGISTRY.values().stream()
              .filter(v -> v != TrainIconType.getDefault())
              .findFirst()
              .orElse(TrainIconType.getDefault());
      proxy.icon = alternate;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.trainName().equals("Native write-through")
              && car.driver.trainMapColor == 7
              && car.driver.trainIcon.equals(alternate.getId()),
          "native metadata writes reach the persistent console");
      var disk = car.driver.saveWithoutMetadata(level.registryAccess());
      helper.assertTrue(
          disk.getString("TrainName").equals("Native write-through")
              && disk.getInt("TrainMapColor") == 7,
          "metadata survives actual console NBT");
      var player = operator(car);
      player.getAbilities().mayBuild = false;
      helper.assertTrue(
          !proxy.editIdentity(player, "Forbidden", alternate.getId(), 2),
          "native edit packet still checks player permission");
      player.getAbilities().mayBuild = true;
      helper.assertTrue(
          proxy.editIdentity(player, "Native write-through", alternate.getId(), 7),
          "nearby permitted player can edit the physical train");
      new com.simibubi.create.content.trains.station.TrainEditPacket.Serverbound(
              proxy.id, "Native packet edit", alternate.getId(), 9)
          .handle(player);
      helper.assertTrue(
          car.driver.trainName().equals("Native packet edit") && car.driver.trainMapColor == 9,
          "actual native edit packet persists through the loaded Mixin");
      new com.simibubi.create.content.trains.station.TrainEditPacket.Serverbound(
              proxy.id, "x".repeat(80), alternate.getId(), Integer.MAX_VALUE)
          .handle(player);
      helper.assertTrue(
          proxy.name.getString().length() <= TrainName.MAX_LENGTH && proxy.mapColorIndex == 15,
          "native edit replies use sanitized authoritative name and color");
      helper.assertTrue(
          car.driver.schedule.item().isEmpty(),
          "remote schedule starts without fabricating a physical item");
      proxy.runtime.setSchedule(program(), false);
      helper.assertTrue(
          car.driver.schedule.present()
              && car.driver.schedule.running()
              && car.driver.schedule.item().isEmpty(),
          "native schedule DATA executes in the physical controller without item handoff");
      var saved = car.driver.saveWithoutMetadata(level.registryAccess());
      var replica = new DriverConsoleEntity(car.driver.getBlockPos(), car.driver.getBlockState());
      replica.setLevel(level);
      replica.loadWithComponents(saved, level.registryAccess());
      helper.assertTrue(
          replica.schedule.present()
              && replica.schedule.resumePending()
              && replica.schedule.total() == 2
              && replica.schedule.item().isEmpty(),
          "remote schedule persists as data and restores through normal readiness");
      start = z(car);
      deadline = level.getGameTime() + 400;
      later(1, this::awaitMotion);
    }

    void awaitMotion() {
      helper.assertTrue(
          level.getGameTime() < deadline, "native schedule starts both physical cars");
      if (start - z(car) < 3 || speed(tail) < .5) {
        later(1, this::awaitMotion);
        return;
      }
      proxy.runtime.paused = true;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          !car.driver.schedule.running(), "public paused field pauses the real controller");
      deadline = level.getGameTime() + 140;
      stable = 0;
      later(1, this::awaitStop);
    }

    void awaitStop() {
      helper.assertTrue(
          level.getGameTime() < deadline, "native pause brakes the complete physical consist");
      if (speed(car) < .1 && speed(tail) < .1) stable++;
      else stable = 0;
      if (stable < 10) {
        later(1, this::awaitStop);
        return;
      }
      helper.assertTrue(
          car.driver.schedule.entry() == 0, "native pause preserves current instruction");
      proxy.runtime.paused = false;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.running(), "public pause release resumes physical scheduling");
      proxy.runtime.currentEntry = 1;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.entry() == 1,
          "native entry selection reaches real schedule progress");
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.entry() == 1, "repeated polls do not replay the selection");
      proxy.throttle = .5;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.throttle() == .5, "native throttle reaches physical schedule");
      proxy.runtime.currentTitle = "Native route title";
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.currentTitle().equals("Native route title"),
          "native route title reaches physical controller state");
      later(
          10,
          () -> {
            proxy
                .runtime
                .schedule
                .entries
                .get(1)
                .conditions
                .getFirst()
                .getFirst()
                .getData()
                .putInt("Value", 31);
            proxy.controls.consume(true);
            helper.assertTrue(
                car.driver
                            .schedule
                            .program()
                            .entries
                            .get(1)
                            .conditions
                            .getFirst()
                            .getFirst()
                            .getData()
                            .getInt("Value")
                        == 31
                    && car.driver.schedule.entry() == 1
                    && car.driver.schedule.throttle() == .5,
                "in-place native program editing writes through once while preserving selection and"
                    + " throttle");
            later(50, this::manualAndCancel);
          });
    }

    void manualAndCancel() {
      double direction = Math.copySign(1, proxy.speed);
      proxy.targetSpeed = direction * .08;
      proxy.approachTargetSpeed(1);
      helper.assertTrue(
          Double.isFinite(car.driver.nativeSpeed) && Math.abs(car.driver.nativeSpeed) == 1.6,
          "native blocks/tick convert once to physical blocks/second");
      helper.assertTrue(
          !car.driver.schedule.running(), "native manual drive suspends automatic scheduling");
      proxy.manualSteer = TravellingPoint.SteerDirection.LEFT;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.routeSelection.intent() != 0,
          "native steering reaches physical junction selection");
      later(
          60,
          () -> {
            helper.assertTrue(
                speed(car) > .4 && speed(car) < 2.1 && speed(tail) > .4,
                "native target speed drives both real carriages within the requested ceiling");
            proxy.manualTick = true;
            NativeTrainBridge.acceptCommands(level);
            proxy.controls
                .unbind(); // withdrawing/rebinding the view must not extend physical input
                           // ownership
            later(
                45,
                () -> {
                  helper.assertTrue(
                      !car.driver.enabled,
                      "per-tick native manual input expires when renewal stops");
                  proxy.navigation.cancelNavigation();
                  helper.assertTrue(
                      !car.driver.enabled && !car.driver.schedule.running(),
                      "native cancellation cuts physical drive");
                  later(80, this::navigation);
                });
          });
    }

    void navigation() {
      helper.assertTrue(
          speed(car) < .1 && speed(tail) < .1, "native cancellation physically stops the consist");
      var path = proxy.navigation.findPathTo(station.getStation(), Double.MAX_VALUE);
      helper.assertTrue(
          path != null && path.destination == station.getStation(),
          "native path query uses the physical route planner");
      helper.assertTrue(
          !car.driver.schedule.nativeActive(), "path query alone cannot start a journey");
      double distance = proxy.navigation.startNavigation(path);
      helper.assertTrue(
          distance > 0 && car.driver.schedule.nativeActive(),
          "native navigation starts a real physical journey");
      var physicalPlan = car.driver.schedule.plan();
      proxy.navigation.startNavigation(path);
      helper.assertTrue(
          car.driver.schedule.plan() == physicalPlan,
          "repeated navigation request retains physical plan and arrival debounce");
      proxy.runtime.paused = false;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          car.driver.schedule.plan() == physicalPlan,
          "resume of an already running native journey cannot reset its plan");
      deadline = level.getGameTime() + 1500;
      later(1, this::arrive);
    }

    void arrive() {
      helper.assertTrue(
          level.getGameTime() < deadline, "native-directed physical train reaches the station");
      if (car.driver.schedule.nativeActive()) {
        later(1, this::arrive);
        return;
      }
      helper.assertTrue(
          !car.driver.schedule.running() && car.driver.schedule.entry() == 1,
          "native journey completes without consuming the onboard schedule entry");
      helper.assertTrue(
          speed(car) < .15 && speed(tail) < .15,
          "native destination completion uses actual stopped physical bodies");
      proxy.crash();
      helper.assertTrue(
          car.driver.emergency && car.unit.command == null && tail.unit.command == null,
          "native crash applies real whole-train emergency brakes");
      proxy.detachFromTracks();
      helper.assertTrue(
          car.unit.detached && tail.unit.detached,
          "native detach releases both sets of real guide constraints");
      proxy.reattachToTracks(level);
      helper.assertTrue(
          !car.unit.detached && !tail.unit.detached,
          "checked native reattachment restores both on-track carriages");
      helper.assertTrue(
          proxy.runtime.returnSchedule(level.registryAccess()).isEmpty()
              && car.driver.schedule.present(),
          "excluded schedule handoff neither creates nor takes an item");
      helper.assertTrue(!proxy.canDisassemble(), "excluded station disassembly remains blocked");
      helper.assertTrue(
          car.chest.getItem(0).getCount() == 17 && tail.chest.getItem(0).getCount() == 17,
          "native operations leave real cargo intact");
      proxy.runtime.discardSchedule();
      helper.assertTrue(
          !car.driver.schedule.present(),
          "discard removes remote program data without a physical item");
      proxy.targetSpeed = .05;
      proxy.approachTargetSpeed(1);
      helper.assertTrue(
          car.driver.enabled && Double.isFinite(car.driver.nativeSpeed),
          "native drive also works without any schedule item or remote program");
      proxy.runtime.paused = false;
      NativeTrainBridge.acceptCommands(level);
      proxy.runtime.paused = true;
      NativeTrainBridge.acceptCommands(level);
      helper.assertTrue(
          !car.driver.enabled && !Double.isFinite(car.driver.nativeSpeed),
          "native pause cannot fall back to ordinary RPM driving when no schedule exists");
      org.slf4j.LoggerFactory.getLogger("physicalrailways.native-control-validation")
          .info(
              "NATIVE_CONTROL_PASS metadata=true pauseResume=true entry=true speed=true"
                  + " steering=true navigation=true emergency=true detachReattach=true"
                  + " itemHandoff=false cargoUntouched=true");
      cleanup();
      helper.succeed();
    }
  }
}
