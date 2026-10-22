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

  private static boolean calls(MethodNode method, String name) {
    for (var insn : method.instructions)
      if (insn instanceof MethodInsnNode call
          && call.owner.equals("com/guidedrailways/ScheduleController")
          && call.name.equals(name)) return true;
    return false;
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
    boolean completeBeforeRestore = false, membershipAfterCargo = false;
    var automatic =
        code("com/guidedrailways/AutomaticTrains").methods.stream()
            .filter(m -> m.name.equals("tick"))
            .findFirst()
            .orElseThrow();
    for (var insn : automatic.instructions) {
      if (!(insn instanceof MethodInsnNode call)) continue;
      if (call.owner.equals("com/guidedrailways/CouplerConnections")
          && call.name.equals("cargoComplete")) completeBeforeRestore = true;
      if (call.owner.equals("com/guidedrailways/CargoStorage") && call.name.equals("consist"))
        membershipAfterCargo = true;
      if (call.owner.equals("com/guidedrailways/ScheduleController") && call.name.equals("restore"))
        check(
            completeBeforeRestore && call.desc.endsWith("Z)Z"),
            "restoration receives coupling readiness");
      if (call.owner.equals("com/guidedrailways/ScheduleController")
          && call.name.equals("acceptMembers"))
        check(
            membershipAfterCargo && call.desc.contains(";Z)"),
            "running membership receives cargo readiness");
    }
    check(completeBeforeRestore && membershipAfterCargo, "both readiness boundaries exist");
    var command =
        code("com/guidedrailways/DriverConsoleEntity").methods.stream()
            .filter(m -> m.name.equals("command"))
            .findFirst()
            .orElseThrow();
    check(calls(command, "resumePending"), "entering pending automatic operation must observe");
    boolean menuPending = false;
    for (var method : code("com/guidedrailways/DriverMenu$1").methods)
      menuPending |= calls(method, "resumePending");
    check(menuPending, "pending automatic operation exposes pause rather than start");
    var controller = code("com/guidedrailways/ScheduleController");
    var stop =
        controller.methods.stream().filter(m -> m.name.equals("stop")).findFirst().orElseThrow();
    for (String field : List.of("running", "resumePending", "reloadGate")) {
      boolean reset = false;
      for (var insn : stop.instructions)
        if (insn instanceof FieldInsnNode f
            && f.getOpcode() == Opcodes.PUTFIELD
            && f.name.equals(field)) reset = true;
      check(reset, "pause and explicit takeover clear automatic recovery: " + field);
    }
    check(calls(transition, "pause"), "explicit topology changes can cancel pending recovery");
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
