package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.tree.*;

/** Enforces coordination boundaries and the order required by shared train state. */
public final class TrainRuntimeCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input =
        TrainRuntimeCheck.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  private static MethodNode method(String owner, String method) throws Exception {
    return code(owner).methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
  }

  private static List<String> calls(String owner, String method) throws Exception {
    var result = new ArrayList<String>();
    for (var instruction : method(owner, method).instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.startsWith("com/guidedrailways/"))
        result.add(call.owner.substring("com/guidedrailways/".length()) + "." + call.name);
    return result;
  }

  /** Progression order only: measurement calls must never hide a reordering of real work. */
  private static List<String> progress(String method) throws Exception {
    return calls("TrainRuntime", method).stream()
        .filter(call -> !call.startsWith("PerfCounters."))
        .toList();
  }

  private static void order(String method, String... expected) throws Exception {
    var actual = progress(method);
    if (!actual.equals(List.of(expected))) throw new AssertionError(method + ": " + actual);
  }

  private static boolean mentions(MethodNode body, String text) {
    for (var instruction : body.instructions)
      if (instruction instanceof LdcInsnNode literal
          && literal.cst instanceof String value
          && value.contains(text)) return true;
    return false;
  }

  private static boolean readsField(MethodNode body, String field) {
    for (var instruction : body.instructions)
      if (instruction instanceof FieldInsnNode access && access.name.equals(field)) return true;
    return false;
  }

  public static void main(String[] args) throws Exception {
    order(
        "beforeTick",
        "CargoStorage.tick",
        "CouplerConnections.restoreLoaded",
        "VehiclePhysics.hasVehicles",
        "TrackNetwork.refresh",
        "VehiclePhysics.refresh",
        "TrainConsists.refresh",
        "TrainConsists.applyChanges",
        "TrainElectricity.refresh",
        "DriverControls.refresh",
        "PhysicalSignals.tick",
        "DriverControls.consoles",
        "AutomaticTrains.tick",
        "StationPresence.tick",
        "DriverControls.publish",
        "VehiclePhysics.update");
    order(
        "afterTick", "NativeTrainBridge.tick", "PhysicalSignals.publish", "VehiclePhysics.activeCount");
    order("physicsStep", "VehiclePhysics.step", "CouplerConnections.step");
    order(
        "unload",
        "VehiclePhysics.unload",
        "TrackNetwork.unload",
        "CouplerConnections.unload",
        "DriverControls.unload",
        "TrainConsists.unload",
        "NativeTrainBridge.unload",
        "CargoStorage.unload",
        "RailwaysCompat.unload",
        "TrainElectricity.unload");
    for (String owner : List.of("VehiclePhysics", "DriverControls"))
      for (var method : code(owner).methods)
        for (var instruction : method.instructions)
          if (instruction instanceof MethodInsnNode call
              && ((call.owner.equals("com/guidedrailways/AutomaticTrains")
                      && call.name.equals("tick"))
                  || (call.owner.equals("com/guidedrailways/PhysicalSignals")
                      && call.name.equals("tick"))
                  || (call.owner.equals("com/guidedrailways/StationPresence")
                      && call.name.equals("tick"))
                  || call.owner.equals("com/guidedrailways/TrainRuntime")))
            throw new AssertionError("Global progression hidden in " + owner + "." + method.name);

    // The performance split must stay wired to the real phases and stay opt-in.
    check(
        readsField(method("PerfCounters", "begin"), "PERFORMANCE_LOGGING"),
        "timing stays behind the performanceLogging switch");
    check(
        calls("TrainRuntime", "beforeTick").contains("PerfCounters.before"),
        "beforeTick phase timing present");
    check(
        calls("TrainRuntime", "afterTick").contains("PerfCounters.after"),
        "afterTick phase timing present");
    check(
        calls("TrainRuntime", "physicsStep").contains("PerfCounters.coupler"),
        "coupler substep is measured separately from guidance");
    check(
        calls("VehiclePhysics", "stepVehicle").contains("PerfCounters.contacts"),
        "bogie contact reconciliation is measured separately");
    check(
        calls("VehiclePhysics", "stepVehicle").contains("PerfCounters.switchQuery"),
        "junction distance queries are measured separately");
    check(
        calls("TrainRuntime", "afterTick").contains("PerfCounters.report")
            && !calls("VehiclePhysics", "update").contains("PerfCounters.report"),
        "one report per window, emitted after both tick phases");
    var report = method("PerfCounters", "report");
    check(
        mentions(report, "[GuidedPerf]") && mentions(report, "[GuidedPerfSplit]"),
        "the established summary line survives next to the new split line");
    check(
        mentions(method("DriverConsoleEntity", "saveAdditional"), "Notch")
            && mentions(method("DriverConsoleEntity", "loadAdditional"), "Notch"),
        "the lever setting is saved and restored, while the takeover session stays unloaded");
    check(
        calls("VehiclePhysics", "connectPivot").contains("BearingLock.locked"),
        "pivot rigidity is decided by the checked bearing policy, not by a literal axis list");
    check(
        calls("VehiclePhysics", "stepVehicle").contains("ConsistRoutes.prepare"),
        "routing still follows the early return for an empty unit set");
    System.out.println(
        "TrainRuntimeCheck: "
            + checks
            + " tick ordering, unload, coordination boundary and performance-split wiring checks"
            + " passed");
  }
}
