package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.tree.*;

/** Enforces coordination boundaries and the order required by shared train state. */
public final class TrainRuntimeCheck {
  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input =
        TrainRuntimeCheck.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  private static List<String> calls(String owner, String method) throws Exception {
    var result = new ArrayList<String>();
    var body =
        code(owner).methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
    for (var instruction : body.instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.startsWith("com/guidedrailways/"))
        result.add(call.owner.substring("com/guidedrailways/".length()) + "." + call.name);
    return result;
  }

  private static void order(String method, String... expected) throws Exception {
    var actual = calls("TrainRuntime", method);
    if (!actual.equals(List.of(expected))) throw new AssertionError(method + ": " + actual);
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
    order("afterTick", "NativeTrainBridge.tick", "PhysicalSignals.publish");
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
    System.out.println(
        "TrainRuntimeCheck: tick ordering, unload and coordination boundaries passed");
  }
}
