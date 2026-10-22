package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Exercise production schedule timing and enforce runtime mutation boundaries without mod loading.
 */
public final class ScheduleLifecycleCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = ScheduleLifecycleCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    var topology =
        code("com/guidedrailways/DriverControls").methods.stream()
            .filter(m -> m.name.equals("topologyChanged"))
            .findFirst()
            .orElseThrow();
    for (var insn : topology.instructions)
      if (insn instanceof MethodInsnNode call
          && call.owner.equals("com/guidedrailways/RailDocks")
          && call.name.equals("release"))
        throw new AssertionError("topology caller cannot release the same lease a second time");
    int releases = 0;
    var transition =
        code("com/guidedrailways/ScheduleController").methods.stream()
            .filter(m -> m.name.equals("topologyChanged"))
            .findFirst()
            .orElseThrow();
    for (var insn : transition.instructions)
      if (insn instanceof MethodInsnNode call
          && call.owner.equals("com/guidedrailways/RailDocks")
          && call.name.equals("release")) releases++;
    check(releases == 1, "the topology transition owns exactly one dock release");
    var cadence = new ScheduleCadence();
    check(cadence.beginPlanning(100), "first route request allowed");
    for (int tick = 100; tick < 110; tick++)
      check(!cadence.beginPlanning(tick), "planning retry is throttled");
    check(cadence.beginPlanning(110), "planning resumes at deadline");
    for (int i = 0; i < 5; i++) check(!cadence.arrivalStable(), "arrival needs six stable samples");
    check(cadence.arrivalStable(), "sixth stable sample permits arrival");
    cadence.travelling();
    check(!cadence.arrivalStable(), "departure resets arrival debounce");
    cadence.restartPlanning();
    check(cadence.beginPlanning(110), "route invalidation clears previous deadline");
    cadence.reset();
    check(cadence.beginPlanning(0) && !cadence.arrivalStable(), "disk reload resets both clocks");
    for (var field : code("com/guidedrailways/ScheduleController").fields)
      if ((field.access & Opcodes.ACC_STATIC) == 0 && !field.name.equals("owner"))
        check((field.access & Opcodes.ACC_PRIVATE) != 0, "runtime state is private: " + field.name);
    for (String name :
        List.of(
            "AutomaticTrains",
            "DriverControls",
            "ConsistRoutes",
            "PackageMail",
            "NativeTrainJourney"))
      for (var method : code("com/guidedrailways/" + name).methods) {
        boolean isolated = true;
        for (var insn : method.instructions)
          if (insn instanceof FieldInsnNode f
              && f.getOpcode() == Opcodes.PUTFIELD
              && f.owner.equals("com/guidedrailways/ScheduleController")) isolated = false;
        check(
            isolated,
            "external systems cannot partially rewrite runtime state: " + name + "." + method.name);
      }
    for (var method : code("com/guidedrailways/VehiclePlacement").methods) {
      boolean isolated = true;
      for (var insn : method.instructions)
        if (insn instanceof MethodInsnNode call
            && ((call.owner.equals("com/guidedrailways/VehiclePhysics")
                    && (call.name.equals("refresh") || call.name.equals("update")))
                || call.owner.equals("com/guidedrailways/TrainRuntime"))) isolated = false;
      check(isolated, "construction cannot advance every train: " + method.name);
    }
    System.out.println(
        "ScheduleLifecycleCheck: "
            + checks
            + " controller transition and execution-boundary checks passed; no world started");
  }
}
