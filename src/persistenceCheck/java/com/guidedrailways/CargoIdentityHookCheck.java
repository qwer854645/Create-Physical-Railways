package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.tree.*;

/**
 * Verify the real Minecraft lifecycle targets and their connection to shared-cache invalidation.
 */
public final class CargoIdentityHookCheck {
  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = CargoIdentityHookCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  private static void hook(String target, String name, String descriptor, String mixin)
      throws Exception {
    if (code(target).methods.stream()
        .noneMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)))
      throw new AssertionError("Minecraft lifecycle target changed: " + target + "." + name);
    boolean invalidates = false, injects = false;
    for (var method : code("com/guidedrailways/mixin/" + mixin).methods) {
      if (method.visibleAnnotations != null)
        for (var annotation : method.visibleAnnotations)
          if (annotation.desc.endsWith("/Inject;") && annotation.values != null)
            for (var value : annotation.values)
              if (value instanceof List<?> names && names.contains(name)) injects = true;
      for (var instruction : method.instructions)
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals("com/guidedrailways/CargoStorage")
            && call.name.equals("blockEntitiesChanged")) invalidates = true;
    }
    if (!injects || !invalidates)
      throw new AssertionError("Missing identity invalidation hook: " + mixin);
  }

  public static void main(String[] args) throws Exception {
    hook(
        "net/minecraft/world/level/block/entity/BlockEntity",
        "setRemoved",
        "()V",
        "CargoEntityRemovalMixin");
    hook(
        "net/minecraft/world/level/chunk/LevelChunk",
        "setBlockEntity",
        "(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        "CargoEntityInsertionMixin");
    boolean memoized = false;
    for (var method : code("com/guidedrailways/CargoStorage").methods)
      if (method.name.equals("snapshot"))
        for (var instruction : method.instructions)
          if (instruction instanceof MethodInsnNode call
              && call.owner.equals("com/guidedrailways/StorageIdentities")
              && call.name.equals("matches")
              && call.desc.equals("(JLjava/util/function/Function;)Z")) memoized = true;
    if (!memoized)
      throw new AssertionError("Cargo snapshots must share per-tick identity validation");
    System.out.println(
        "CargoIdentityHookCheck: Minecraft insertion/removal targets and cache invalidation contracts passed; no game started");
  }
}
