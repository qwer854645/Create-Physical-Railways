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
    var buffers = new HashMap<ResourceLocation, Object>();
    var manual = new HashSet<ResourceLocation>();
    for (var entry : Map.of("framebuffers", buffers, "manualFramebuffers", manual).entrySet()) {
      var field = VeilFramebufferRegistrationMixin.class.getDeclaredField(entry.getKey());
      field.setAccessible(true);
      field.set(target, entry.getValue());
    }
    var method = VeilFramebufferRegistrationMixin.class.getDeclaredMethod(
        "guidedrailways$reuseRegistration", ResourceLocation.class, Object.class, CallbackInfo.class);
    method.setAccessible(true);
    var name = ResourceLocation.fromNamespaceAndPath("veil", "first_person");
    var original = new Object();
    var ci = new CallbackInfo("setFramebuffer", true);
    method.invoke(target, name, original, ci);
    check(!ci.isCancelled(), "first registration runs native initialization");
    buffers.put(name, original);
    ci = new CallbackInfo("setFramebuffer", true);
    method.invoke(target, name, original, ci);
    check(!ci.isCancelled(), "defined-to-manual conversion runs native cleanup");
    manual.add(name);
    for (int i = 0; i < 10000; i++) {
      ci = new CallbackInfo("setFramebuffer", true);
      method.invoke(target, name, original, ci);
      check(ci.isCancelled() != disabled, "identical live registration skips driver relabeling");
    }
    var replacement = new Object();
    ci = new CallbackInfo("setFramebuffer", true);
    method.invoke(target, name, replacement, ci);
    check(!ci.isCancelled(), "new framebuffer runs native registration");
    buffers.clear(); manual.clear();
    ci = new CallbackInfo("setFramebuffer", true);
    method.invoke(target, name, original, ci);
    check(!ci.isCancelled(), "resource reload cannot inherit a stale registration");
    check(buffers.isEmpty() && manual.isEmpty(), "guard does not mutate Veil registry");
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
          found = true; break;
        }
      }
      check(found, "native manager checked");
    }
    System.out.println("VeilFramebufferReuseCheck: repeated registration, conversion, replacement and reload passed; disabled=" + disabled);
  }
}
