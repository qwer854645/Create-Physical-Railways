package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;

/** Coordinates world-wide train work; construction and query adapters must not advance it. */
final class TrainRuntime {
  private TrainRuntime() {}

  static void beforeTick(ServerLevel level) {
    long start = PerfCounters.begin();
    try {
      CargoStorage.tick(level);
      CouplerConnections.restoreLoaded(level);
      if (VehiclePhysics.hasVehicles(level)) TrackNetwork.refresh(level);
      if (SubLevelContainer.getContainer(level) == null || SubLevelPhysicsSystem.get(level) == null)
        return;
      var units = VehiclePhysics.refresh(level);
      TrainConsists.refresh(level, units);
      TrainConsists.applyChanges(level);
      TrainElectricity.refresh(level);
      DriverControls.refresh(level, units);
      PhysicalSignals.tick(level);
      AutomaticTrains.tick(level, units, DriverControls.consoles(level));
      StationPresence.tick(level);
      DriverControls.publish(level);
      VehiclePhysics.update(level, units);
    } finally {
      PerfCounters.before(level, PerfCounters.elapsed(start));
    }
  }

  static void afterTick(ServerLevel level) {
    long start = PerfCounters.begin();
    try {
      NativeTrainBridge.tick(level);
      PhysicalSignals.publish(level);
    } finally {
      PerfCounters.after(level, PerfCounters.elapsed(start));
    }
    // Reported here so one window contains both tick phases and every physics substep between them.
    PerfCounters.report(level, VehiclePhysics.activeCount(level));
  }

  static void physicsStep(SubLevelPhysicsSystem system, double timeStep) {
    VehiclePhysics.step(system, timeStep);
    long start = PerfCounters.begin();
    try {
      CouplerConnections.step(system);
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
    TrainElectricity.unload(level);
  }
}
