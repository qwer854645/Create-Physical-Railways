package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.tree.*;

public final class RenderOptimizationCompatCheck {
  private static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }
  public static void main(String[] args) throws Exception {
    String shader = "ShaderSamplerLocationMixin";
    var node = new ClassNode();
    try (var input = RenderOptimizationCompatCheck.class.getResourceAsStream("/net/minecraft/client/renderer/ShaderInstance.class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node, 0);
    }
    check(RenderOptimizationSupport.matches(shader, node), "installed shader eligible");
    var method = node.methods.stream().filter(m -> m.name.equals("apply")).findFirst().orElseThrow();
    method.instructions.insert(new MethodInsnNode(184, "other/mod/ShaderPipeline", "apply", "()V", false));
    check(!RenderOptimizationSupport.matches(shader, node), "foreign shader hook conservatively disables reuse");
    check(!RenderOptimizationSupport.matches(shader, new ClassNode()), "missing shadow fields safely rejected");
    for (String name : List.of(shader, "VeilFramebufferRegistrationMixin", "VeilFramebufferSnapshotMixin")) {
      String property = RenderOptimizationSupport.option(name);
      try {
        System.setProperty(property, "true");
        check(RenderOptimizationSupport.disabled(name), "per-feature disable recognized before injection");
      } finally { System.clearProperty(property); }
    }
    try {
      System.setProperty("guidedrailways.disableRenderOptimizations", "true");
      check(RenderOptimizationSupport.disabled(shader)
          && RenderOptimizationSupport.disabled("VeilFramebufferRegistrationMixin")
          && RenderOptimizationSupport.disabled("VeilFramebufferSnapshotMixin"), "global bypass recognized");
    } finally { System.clearProperty("guidedrailways.disableRenderOptimizations"); }
    System.out.println("RenderOptimizationCompatCheck: native target, foreign hooks, absent fields and early opt-outs passed");
  }
}
