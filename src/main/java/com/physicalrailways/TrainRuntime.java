package com.physicalrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;

/** Coordinates world-wide train work; construction and query adapters must not advance it. */
final class TrainRuntime {
  private TrainRuntime() {}

  static void beforeTick(ServerLevel level) {
    if (!TrainFaults.phaseReady(level, "world_before")) return;
    long start = PerfCounters.begin();
    try {
      SchedulePriority.tick(level);
      SwitchBlockedReasons.tick(level);
      ApproachRooms.tick(level);
      BranchWatch.tick(level);
      RailwaysCompat.tick(level);
      CargoStorage.tick(level);
      CouplerConnections.restoreLoaded(level);
      if (VehiclePhysics.hasVehicles(level)) TrackNetwork.refresh(level);
      if (SubLevelContainer.getContainer(level) == null || SubLevelPhysicsSystem.get(level) == null)
        return;
      var units = VehiclePhysics.refresh(level);
      TrainConsists.refresh(level, units);
      TrainConsists.applyChanges(level);
      TrainFaults.begin(level, TrainConsists.get(level));
      TrainElectricity.refresh(level);
      DriverControls.refresh(level, units);
      PhysicalSignals.tick(level);
      AutomaticTrains.tick(level, units, DriverControls.consoles(level));
      StationPresence.tick(level);
      DriverControls.publish(level);
      VehiclePhysics.update(level, units);
      TrainFaults.phaseHealthy(level, "world_before");
    } catch (RuntimeException error) {
      TrainFaults.phaseFailed(level, "world_before", error);
    } finally {
      PerfCounters.before(level, PerfCounters.elapsed(start));
    }
  }

  static void afterTick(ServerLevel level) {
    if (!TrainFaults.phaseReady(level, "world_after")) return;
    long start = PerfCounters.begin();
    try {
      NativeTrainBridge.tick(level);
      PhysicalSignals.publish(level);
      TrainFaults.phaseHealthy(level, "world_after");
    } catch (RuntimeException error) {
      TrainFaults.phaseFailed(level, "world_after", error);
    } finally {
      PerfCounters.after(level, PerfCounters.elapsed(start));
    }
    // Reported here so one window contains both tick phases and every physics substep between them.
    PerfCounters.report(level, VehiclePhysics.activeCount(level));
  }

  static void physicsStep(SubLevelPhysicsSystem system, double timeStep) {
    var level = system.getLevel();
    if (TrainFaults.phaseReady(level, "physics")) {
      try {
        VehiclePhysics.step(system, timeStep);
        TrainFaults.phaseHealthy(level, "physics");
      } catch (RuntimeException error) {
        TrainFaults.phaseFailed(level, "physics", error);
        TrainFaults.brakeHeld(system, timeStep);
      }
    } else TrainFaults.brakeHeld(system, timeStep);
    long start = PerfCounters.begin();
    try {
      if (TrainFaults.phaseReady(level, "coupling")) {
        CouplerConnections.step(system);
        TrainFaults.phaseHealthy(level, "coupling");
      }
    } catch (RuntimeException error) {
      TrainFaults.phaseFailed(level, "coupling", error);
    } finally {
      PerfCounters.coupler(system.getLevel(), PerfCounters.elapsed(start));
    }
  }

  static void unload(ServerLevel level) {
    VehiclePhysics.unload(level);
    TrackNetwork.unload(level);
    CouplerConnections.unload(level);
    DriverControls.unload(level);
    TrainConsists.unload(level);
    NativeTrainBridge.unload(level);
    CargoStorage.unload(level);
    RailwaysCompat.unload(level);
    SchedulePriority.unload(level);
    SwitchBlockedReasons.unload(level);
    ApproachRooms.unload(level);
    BranchWatch.unload(level);
    TrainElectricity.unload(level);
    TrainFaults.unload(level);
  }
}
