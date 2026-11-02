package com.guidedrailways;

import com.guidedrailways.mixin.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.MeshData;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.*;
import jdk.jfr.consumer.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real target signatures and transparent wrapper behavior, with no OpenGL context or game launch. */
public final class LagProbeCheck {
  private static int checks;
  private static void check(boolean result, String why) { checks++; if (!result) throw new AssertionError(why); }
  private static ClassNode node(byte[] bytes) { var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
  public static void main(String[] args) throws Exception {
    boolean enabled = Boolean.getBoolean("guidedrailways.lagProbe");
    check(LagProbe.enabled() == enabled, "explicit startup opt-in");
    check(LagProbe.skipPhysicalPortals() == (enabled && Boolean.getBoolean("guidedrailways.skipPhysicalPortalChecks")), "A/B bypass requires diagnostic mode and its own explicit opt-in");
    try (var input = LagProbeCheck.class.getClassLoader().getResourceAsStream("com/guidedrailways/PhysicalPortals.class")) {
      var portals = node(input.readAllBytes());
      var tick = portals.methods.stream().filter(method -> method.name.equals("tick")).findFirst().orElseThrow();
      var calls = new ArrayList<MethodInsnNode>();
      for (var instruction : tick.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
      check(calls.getFirst().owner.equals("com/guidedrailways/LagProbe") && calls.getFirst().name.equals("skipPhysicalPortals"), "bypass is checked before level, physics, POI or target chunk access");
      var branch = calls.getFirst().getNext();
      while (branch != null && branch.getOpcode() < 0) branch = branch.getNext();
      check(branch instanceof JumpInsnNode && branch.getOpcode() == Opcodes.IFEQ, "enabled bypass branches to an immediate return");
      var returned = branch.getNext(); while (returned != null && returned.getOpcode() < 0) returned = returned.getNext();
      check(returned.getOpcode() == Opcodes.RETURN, "A/B mode does no portal work");
    }
    check((LagProbe.begin() != 0) == enabled, "no timer while disabled");
    if (!enabled) check(!new CargoCompatPlugin().shouldApplyMixin("absent.Target", "com.guidedrailways.mixin.LagProbeWorldMixin"), "default launch skips before inspecting or transforming renderer");
    var targets = Map.of("LagProbeWorldMixin", "net/minecraft/client/renderer/LevelRenderer",
        "LagProbeUploadsMixin", "net/minecraft/client/renderer/chunk/SectionRenderDispatcher",
        "LagProbeVertexMixin", "com/mojang/blaze3d/vertex/VertexBuffer");
    for (var target : targets.entrySet()) {
      try (var input = LagProbeCheck.class.getClassLoader().getResourceAsStream(target.getValue() + ".class")) {
        check(input != null, "mapped target exists"); var original = node(input.readAllBytes());
        check(LagProbe.supports(target.getKey(), original), "current mapped method signatures supported");
        for (var method : original.methods) method.access |= Opcodes.ACC_STATIC;
        check(!LagProbe.supports(target.getKey(), original), "static replacements reject instance wrappers");
        original.methods.clear(); check(!LagProbe.supports(target.getKey(), original), "changed targets safely reject probes");
      }
    }
    try (var zip = new java.util.zip.ZipFile("libs/sable-neoforge-1.21.1-2.0.6.jar")) {
      try (var input = zip.getInputStream(zip.getEntry("dev/ryanhcode/sable/network/packets/tcp/ClientboundPhysicsPropertyPacket.class"))) {
        check(LagProbe.supports("LagProbePropertiesMixin", node(input.readAllBytes())), "actual packet execution callback supported");
      }
      for (String name : List.of("VanillaSingleSubLevelRenderData", "VanillaChunkedSubLevelRenderData")) {
        var entry = zip.getEntry("dev/ryanhcode/sable/sublevel/render/vanilla/" + name + ".class");
        check(entry != null, "Sable release target exists");
        try (var input = zip.getInputStream(entry)) { check(LagProbe.supports("LagProbeSableReleaseMixin", node(input.readAllBytes())), "Sable release signature supported"); }
      }
    }
    check(!LagProbe.supports("UnknownProbe", new ClassNode()), "unknown probes never apply");
    var instances = List.of(new LagProbeWorldMixin() {}, new LagProbeUploadsMixin() {},
        new LagProbeVertexMixin() {}, new LagProbeSableReleaseMixin() {}, new LagProbePropertiesMixin() {});
    var mesh = new MeshData(null, null); // Passed through, never uploaded or closed by the probe test.
    for (var instance : instances) for (var method : instance.getClass().getSuperclass().getDeclaredMethods()) {
      if (!method.getName().startsWith("guidedrailways$")) continue;
      method.setAccessible(true); int[] calls = {0};
      Operation<Void> original = values -> {
        calls[0]++; if (method.getParameterCount() == 2) check(values.length == 1 && values[0] == mesh, "mesh reference passed unchanged"); return null;
      };
      Object[] parameters = method.getParameterCount() == 2 ? new Object[] {mesh, original} : new Object[] {original};
      method.invoke(instance, parameters); check(calls[0] == 1, "original operation executes exactly once");
      var failure = new IllegalStateException("injected native failure");
      Operation<Void> broken = values -> { calls[0]++; throw failure; };
      parameters[parameters.length - 1] = broken;
      try { method.invoke(instance, parameters); throw new AssertionError("swallowed original failure"); }
      catch (InvocationTargetException thrown) { check(thrown.getCause() == failure, "original exception identity preserved"); }
      check(calls[0] == 2, "failure is not retried");
    }
    if (enabled) {
      Path file = Files.createTempFile("guided-lag-probe-", ".jfr");
      try (var recording = new Recording()) {
        recording.enable("guidedrailways.LagSpan"); recording.start();
        LagProbe.end(LagProbe.Stage.BOGIE_REMOVE, System.nanoTime() - 20_000_000);
        recording.stop(); recording.dump(file);
        var events = RecordingFile.readAllEvents(file).stream().filter(e -> e.getEventType().getName().equals("guidedrailways.LagSpan")).toList();
        check(events.size() == 1, "slow operation emits one actual JFR record");
        check(events.getFirst().getString("stage").equals("BOGIE_REMOVE") && events.getFirst().getLong("elapsedNanos") >= 20_000_000, "JFR component and measured nanoseconds retained");
      } finally { Files.deleteIfExists(file); }
    }
    System.out.println("LagProbeCheck: " + checks + " signature, passthrough, failure and recording checks passed; enabled=" + enabled);
  }
}
