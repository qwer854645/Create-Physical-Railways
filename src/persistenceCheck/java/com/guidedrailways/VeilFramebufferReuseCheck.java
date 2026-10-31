package com.guidedrailways;

import com.guidedrailways.mixin.VeilFramebufferRegistrationMixin;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class VeilFramebufferReuseCheck {
  private static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    boolean disabled = Boolean.getBoolean("guidedrailways.disableFramebufferRegistrationReuse");
    var target = new VeilFramebufferRegistrationMixin() {};
    var buffers = new HashMap<Object, Object>();
    var put = VeilFramebufferRegistrationMixin.class.getDeclaredMethod("guidedrailways$observeRegistration",
        Map.class, Object.class, Object.class, com.llamalad7.mixinextras.injector.wrapoperation.Operation.class,
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef.class);
    var label = VeilFramebufferRegistrationMixin.class.getDeclaredMethod("guidedrailways$label", Object.class,
        int.class, int.class, CharSequence.class, com.llamalad7.mixinextras.injector.wrapoperation.Operation.class,
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef.class);
    put.setAccessible(true); label.setAccessible(true);
    int[] counts = {0, 0};
    com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> registry = values -> {
      counts[0]++;
      return ((Map<Object, Object>) values[0]).put(values[1], values[2]);
    };
    com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> naming = values -> { counts[1]++; return null; };
    class Flag implements com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef {
      boolean value;
      public boolean get() { return value; }
      public void set(boolean value) { this.value = value; }
    }
    var name = ResourceLocation.fromNamespaceAndPath("veil", "first_person");
    var original = new Object();
    var first = new Flag();
    check(put.invoke(target, buffers, name, original, registry, first) == null, "native put return preserved");
    label.invoke(target, new Object(), 36160, 7, "first person", naming, first);
    check(counts[1] == 1, "first registration gets a label");
    for (int i = 0; i < 10000; i++) {
      var repeated = new Flag();
      check(put.invoke(target, buffers, name, original, registry, repeated) == original, "native map put still executes");
      label.invoke(target, new Object(), 36160, 7, "first person", naming, repeated);
    }
    check(counts[0] == 10001, "other wrappers and map side effects are never bypassed");
    check(counts[1] == (disabled ? 10001 : 1), "only duplicate naming calls suppressed");
    var replacement = new Object();
    var changed = new Flag();
    put.invoke(target, buffers, name, replacement, registry, changed);
    check(!changed.get(), "replacement needs labeling");
    buffers.clear();
    var reloaded = new Flag();
    put.invoke(target, buffers, name, original, registry, reloaded);
    check(!reloaded.get(), "resource reload has no stale identity");
    var missingPutHook = new Flag();
    int before = counts[1];
    label.invoke(target, new Object(), 36160, 9, "fallback", naming, missingPutHook);
    check(counts[1] == before + 1, "missing observation hook leaves naming untouched");
    // Verify installed Veil's method shape without loading Veil or starting an OpenGL context.
    try (var sable = new java.util.zip.ZipFile("libs/sable-neoforge-1.21.1-2.0.6.jar")) {
      var veil = sable.getEntry("META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar");
      check(veil != null, "supported embedded Veil available");
      boolean found = false;
      try (var jar = new java.util.jar.JarInputStream(sable.getInputStream(veil))) {
        for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
          if (!entry.getName().equals("foundry/veil/api/client/render/framebuffer/FramebufferManager.class")) continue;
          var node = new org.objectweb.asm.tree.ClassNode();
          new org.objectweb.asm.ClassReader(jar.readAllBytes()).accept(node, 0);
          check(RenderOptimizationSupport.matches("VeilFramebufferRegistrationMixin", node), "runtime guard accepts installed manager");
          var nativeMethod = node.methods.stream().filter(m -> m.name.equals("setFramebuffer")
              && m.desc.equals("(Lnet/minecraft/resources/ResourceLocation;Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;)V"))
              .findFirst().orElseThrow();
          var calls = new HashSet<String>();
          for (var insn : nativeMethod.instructions)
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call) calls.add(call.owner + "." + call.name);
          check(calls.equals(Set.of("java/util/Set.add", "java/util/Map.remove", "java/util/Map.put",
              "foundry/veil/api/client/render/framebuffer/AdvancedFbo.free", "org/slf4j/Logger.warn",
              "foundry/veil/api/client/render/framebuffer/AdvancedFbo.getId", "foundry/veil/api/client/render/ext/VeilDebug.get",
              "foundry/veil/api/client/render/ext/VeilDebug.objectLabel", "java/lang/String.valueOf")),
              "native registration has no framebuffer binding or extra side effects");
          for (var insn : nativeMethod.instructions)
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("objectLabel")) {
              nativeMethod.instructions.remove(insn); break;
            }
          check(!RenderOptimizationSupport.matches("VeilFramebufferRegistrationMixin", node), "changed label target safely rejected");
          found = true; break;
        }
      }
      check(found, "native manager checked");
    }
    System.out.println("VeilFramebufferReuseCheck: repeated registration, conversion, replacement and reload passed; disabled=" + disabled);
  }
}
