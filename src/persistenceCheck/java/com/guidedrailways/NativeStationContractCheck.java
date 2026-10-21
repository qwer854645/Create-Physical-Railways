package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.tree.*;

/**
 * Check the actual installed native station and integration contracts without launching Minecraft.
 */
public final class NativeStationContractCheck {
  private static int checks;

  private static void check(boolean ok, String label) {
    checks++;
    if (!ok) throw new AssertionError(label);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = NativeStationContractCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static List<MethodInsnNode> calls(MethodNode method) {
    var out = new ArrayList<MethodInsnNode>();
    for (var i : method.instructions) if (i instanceof MethodInsnNode m) out.add(m);
    return out;
  }

  private static MethodNode method(ClassNode code, String name) {
    return code.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
  }

  public static void main(String[] args) throws Exception {
    var station = code("com/simibubi/create/content/trains/station/GlobalStation");
    check(
        calls(method(station, "getPresentTrain")).stream()
            .anyMatch(m -> m.name.equals("getCurrentStation")),
        "native presence validates both sides of station association");
    var cancel = method(station, "cancelReservation");
    check(
        java.util.stream.StreamSupport.stream(cancel.instructions.spliterator(), false)
            .anyMatch(i -> i.getOpcode() == org.objectweb.asm.Opcodes.IF_ACMPNE),
        "native cancellation protects foreign owners");
    for (var name : List.of("reserveFor", "trainDeparted", "getPresentTrain", "getImminentTrain"))
      check(method(station, name) != null, "required station API: " + name);
    var be = code("com/simibubi/create/content/trains/station/StationBlockEntity");
    for (var name :
        List.of(
            "applyAutoSchedule",
            "dropSchedule",
            "enterAssemblyMode",
            "tryEnterAssemblyMode",
            "tryDisassembleTrain"))
      check(method(be, name) != null, "required native station hook: " + name);
    check(
        calls(method(be, "tick")).stream().anyMatch(m -> m.name.equals("getImminentTrain")),
        "native station tick uses linked proxy");
    var presence = code("com/guidedrailways/StationPresence");
    for (var method : presence.methods)
      for (var call : calls(method)) {
        check(
            !Set.of("arriveAt", "destinationReached", "runMailTransfer").contains(call.name),
            "association must not run native scheduling or mail: " + method.name);
      }
    var mixin = code("com/guidedrailways/mixin/StationBlockEntityMixin");
    check(mixin.fields.isEmpty(), "no separate presence flag or client overlay remains");
    check(
        calls(method(mixin, "guidedrailways$keepScheduleInControls")).stream()
            .anyMatch(m -> m.name.equals("cancel")),
        "native automatic schedules cannot start a second runtime on the proxy");
    for (var hook :
        List.of(
            "guidedrailways$physicalAssemblyHint",
            "guidedrailways$preventPhysicalAssembly",
            "guidedrailways$preventPhysicalDisassembly"))
      check(
          calls(method(mixin, hook)).stream().anyMatch(m -> m.name.equals("blocksAssembly")),
          "all native assembly entry points check the live association: " + hook);
    check(
        calls(method(presence, "blocksAssembly")).stream()
            .anyMatch(m -> m.name.equals("getNearestTrain")),
        "assembly protection does not rely only on the debounced position cache");
    var nativeEdit =
        method(code("com/simibubi/create/content/trains/station/TrainEditPacket"), "handleSided");
    check(
        nativeEdit.desc.equals("(Lnet/minecraft/world/entity/player/Player;)V"),
        "native train editing hook has the supported signature");
    check(
        calls(
                method(
                    code("com/guidedrailways/mixin/PhysicalTrainEditMixin"),
                    "guidedrailways$keepPhysicalIdentity"))
            .stream()
            .anyMatch(m -> m.name.equals("cancel")),
        "native train editing cannot write into a physical query proxy");
    var screen = code("com/simibubi/create/content/trains/station/StationScreen");
    for (var hook : List.of("tick", "syncTrainNameAndColor"))
      check(method(screen, hook) != null, "native station screen hook exists: " + hook);
    var screenMixin = code("com/guidedrailways/mixin/PhysicalStationScreenMixin");
    check(
        calls(method(screenMixin, "guidedrailways$readOnlyIdentity")).stream()
            .anyMatch(m -> m.name.equals("cancel")),
        "physical station views do not send native edits");
    check(
        calls(method(screenMixin, "guidedrailways$physicalStationView")).stream()
            .anyMatch(m -> m.name.equals("setValue")),
        "open station views refresh the controls' name");
    check(clientCodeHasGraphRefresh(), "client retries native graph and station synchronization");
    var mail = method(code("com/guidedrailways/PackageMail"), "exchange");
    check(
        calls(mail).stream()
            .anyMatch(
                m ->
                    m.owner.equals("com/guidedrailways/NativeTrainBridge")
                        && m.name.equals("registered")),
        "mail checks the owning physical train instead of treating itself as foreign occupancy");
    // Lambdas hold the packet handler; examine every generated method too.
    var clientCode = code("com/guidedrailways/NativeTrainClient");
    check(
        clientCode.methods.stream()
                .flatMap(m -> calls(m).stream())
                .filter(m -> m.name.equals("syncStation"))
                .count()
            == 2,
        "client packet handler binds on updates and releases on removal");
    System.out.println(
        "NativeStationContractCheck: "
            + checks
            + " native station and integration contract checks passed");
  }

  private static boolean clientCodeHasGraphRefresh() throws Exception {
    return code("com/guidedrailways/NativeTrainClient").methods.stream()
        .flatMap(m -> calls(m).stream())
        .anyMatch(m -> m.name.equals("refreshClientGraph"));
  }
}
