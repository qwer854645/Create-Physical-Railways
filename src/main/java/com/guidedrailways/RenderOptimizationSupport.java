package com.guidedrailways;

import java.util.Map;
import org.objectweb.asm.tree.*;

/** Early, class-loading-safe controls for optional render experiments. */
public final class RenderOptimizationSupport {
  private static final Map<String, String> OPTIONS = Map.of(
      "ShaderSamplerLocationMixin", "guidedrailways.disableSamplerLocationReuse",
      "VeilFramebufferRegistrationMixin", "guidedrailways.disableFramebufferRegistrationReuse",
      "VeilFramebufferSnapshotMixin", "guidedrailways.disableFramebufferSnapshotReuse");
  private RenderOptimizationSupport() {}
  public static String option(String name) { return OPTIONS.get(name); }
  public static boolean disabled(String name) {
    return Boolean.getBoolean("guidedrailways.disableRenderOptimizations")
        || option(name) != null && Boolean.getBoolean(option(name));
  }
  private static long calls(MethodNode method, String owner, String name, String desc) {
    long count = 0;
    for (var insn : method.instructions)
      if (insn instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc)) count++;
    return count;
  }
  public static boolean matches(String name, ClassNode node) {
    if (name.equals("VeilFramebufferSnapshotMixin")) return FramebufferSnapshotSupport.matches(node);
    if (name.equals("ShaderSamplerLocationMixin")) {
      for (String field : new String[] {"samplerNames", "samplerLocations", "programId"}) {
        String desc = field.equals("programId") ? "I" : "Ljava/util/List;";
        if (node.fields.stream().noneMatch(f -> f.name.equals(field) && f.desc.equals(desc) && (f.access & 8) == 0)) return false;
      }
      for (var m : node.methods) if (m.name.equals("apply") && m.desc.equals("()V")) {
        if (m.visibleAnnotations != null && m.visibleAnnotations.stream().anyMatch(a -> a.desc.contains("MixinMerged"))) return false;
        for (var insn : m.instructions)
          if (insn instanceof MethodInsnNode c && !(c.owner.startsWith("java/")
              || c.owner.startsWith("net/minecraft/") || c.owner.startsWith("com/mojang/"))) return false;
        return calls(m, "com/mojang/blaze3d/shaders/Uniform", "glGetUniformLocation", "(ILjava/lang/CharSequence;)I") == 1;
      }
      return false;
    }
    if (name.equals("VeilFramebufferRegistrationMixin")) {
      for (var m : node.methods) if (m.name.equals("setFramebuffer") && m.desc.equals(
          "(Lnet/minecraft/resources/ResourceLocation;Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;)V"))
        return calls(m, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") == 1
            && calls(m, "foundry/veil/api/client/render/ext/VeilDebug", "objectLabel", "(IILjava/lang/CharSequence;)V") == 1;
    }
    return false;
  }
}
