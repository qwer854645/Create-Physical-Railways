package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Verify the exact vanilla field used to read packed positions without copying its union. */
public final class RecoveryBoundaryCheck {
  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = RecoveryBoundaryCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    var chunk = code("net/minecraft/world/level/chunk/ChunkAccess");
    if (chunk.fields.stream()
        .noneMatch(
            f ->
                f.name.equals("pendingBlockEntities")
                    && f.desc.equals("Ljava/util/Map;")
                    && (f.access & Opcodes.ACC_STATIC) == 0))
      throw new AssertionError("packed block-entity field changed");
    var accessor = code("com/guidedrailways/mixin/ChunkPendingEntitiesAccessor");
    var method =
        accessor.methods.stream()
            .filter(m -> m.name.equals("guidedrailways$pendingEntities"))
            .findFirst()
            .orElseThrow();
    if (!method.desc.equals("()Ljava/util/Map;")
        || method.visibleAnnotations == null
        || method.visibleAnnotations.stream()
            .noneMatch(
                a -> a.desc.endsWith("/Accessor;") && a.values.contains("pendingBlockEntities")))
      throw new AssertionError("accessor must expose the exact read-only map field");
    for (var m : code("com/guidedrailways/CouplerDiscovery").methods)
      for (var instruction : m.instructions)
        if (instruction instanceof MethodInsnNode call
            && (call.name.equals("getBlockEntitiesPos")
                || call.owner.equals("java/util/List") && call.name.equals("copyOf")))
          throw new AssertionError("recovery cannot make an unbudgeted full chunk snapshot");
    System.out.println(
        "RecoveryBoundaryCheck: vanilla packed map, accessor descriptor and no bulk position copying passed");
  }
}
