package com.guidedrailways;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Installed Xaero API contracts and marker interpolation, without starting a renderer. */
public final class MinimapBridgeCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = MinimapBridgeCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream, name)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    var base = "xaero/hud/minimap/element/render/MinimapElement";
    for (var type : List.of("Reader", "RenderProvider", "Renderer")) {
      var nativeClass = code(base + type);
      var own =
          code(
              "com/guidedrailways/XaeroTrainMap$"
                  + (type.equals("RenderProvider") ? "Provider" : type));
      for (var method : nativeClass.methods)
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0)
          check(
              own.methods.stream()
                  .anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
              "adapter implements installed abstract contract: " + method.name + method.desc);
    }
    check(
        code("xaero/hud/minimap/Minimap").methods.stream()
            .anyMatch(m -> m.name.equals("getOverMapRendererHandler") && m.desc.startsWith("()")),
        "native overlay registration getter exists");
    check(
        code(base + "RendererHandler").methods.stream()
            .anyMatch(m -> m.name.equals("add") && m.desc.equals("(L" + base + "Renderer;)V")),
        "native handler accepts this renderer API");
    var marker = new XaeroTrainMap.Marker("Express", new Vec3(2, 4, 6), new Vec3(4, 8, 12), false);
    for (int axis = 0; axis < 3; axis++) {
      check(marker.coordinate(axis, 0) == 2 * (axis + 1), "previous tick position");
      check(marker.coordinate(axis, .5f) == 3 * (axis + 1), "smooth frame midpoint");
      check(marker.coordinate(axis, 1) == 4 * (axis + 1), "current tick position");
      check(marker.coordinate(axis, -1) == marker.coordinate(axis, 0), "negative partial clamped");
      check(marker.coordinate(axis, 2) == marker.coordinate(axis, 1), "late partial clamped");
    }
    var proxy = code("com/guidedrailways/PhysicalTrainProxy");
    var query =
        proxy.methods.stream().filter(m -> m.name.equals("mapPosition")).findFirst().orElseThrow();
    check(
        java.util.stream.StreamSupport.stream(query.instructions.spliterator(), false)
            .anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("getSubLevel")),
        "map position uses loaded physical bodies");
    for (var method : code("com/guidedrailways/XaeroTrainMap").methods)
      for (var instruction : method.instructions)
        if (instruction instanceof MethodInsnNode call)
          check(
              !Set.of("addFreshEntity", "sendToServer", "createWaypoint").contains(call.name),
              "map integration creates no entities, server commands or persistent waypoints");
    System.out.println("MinimapBridgeCheck: " + checks + " API and marker checks passed");
  }
}
