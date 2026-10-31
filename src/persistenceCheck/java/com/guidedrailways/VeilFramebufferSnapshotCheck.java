package com.guidedrailways;

import com.guidedrailways.mixin.VeilFramebufferSnapshotMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.util.*;
import org.lwjgl.opengl.GL30C;
import org.objectweb.asm.tree.*;

public final class VeilFramebufferSnapshotCheck {
  private static class IntCell implements LocalIntRef {
    int value;
    public int get() { return value; }
    public void set(int value) { this.value = value; }
  }
  private static class BoolCell implements LocalBooleanRef {
    boolean value;
    public boolean get() { return value; }
    public void set(boolean value) { this.value = value; }
  }
  private static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }
  public static void main(String[] args) throws Exception {
    boolean disabled = Boolean.getBoolean("guidedrailways.disableFramebufferSnapshotReuse");
    var method = VeilFramebufferSnapshotMixin.class.getDeclaredMethod("guidedrailways$snapshotBinding",
        int.class, Operation.class, LocalIntRef.class, LocalBooleanRef.class);
    method.setAccessible(true);
    for (int draw : new int[] {0, 7, 29}) {
      int[] queries = {0};
      Operation<Integer> driver = values -> {
        queries[0]++;
        return (int) values[0] == GL30C.GL_READ_FRAMEBUFFER_BINDING ? 91 : draw;
      };
      var value = new IntCell(); var seen = new BoolCell();
      check((int) method.invoke(null, GL30C.GL_FRAMEBUFFER_BINDING, driver, value, seen) == draw, "fresh draw binding");
      check((int) method.invoke(null, GL30C.GL_READ_FRAMEBUFFER_BINDING, driver, value, seen) == 91, "independent read binding");
      check((int) method.invoke(null, GL30C.GL_DRAW_FRAMEBUFFER_BINDING, driver, value, seen) == draw, "aliased draw binding");
      check(queries[0] == (disabled ? 3 : 2), "only redundant query removed, including framebuffer zero");
      method.invoke(null, GL30C.GL_FRAMEBUFFER_BINDING, driver, new IntCell(), new BoolCell());
      check(queries[0] == (disabled ? 4 : 3), "new or nested invocation always captures fresh state");
    }
    // Confirm the supported native snapshot has two aliases and no binding changes between them.
    try (var sable = new java.util.zip.ZipFile("libs/sable-neoforge-1.21.1-2.0.6.jar")) {
      try (var jar = new java.util.jar.JarInputStream(sable.getInputStream(
          sable.getEntry("META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar")))) {
        boolean found = false;
        for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
          if (!entry.getName().equals("foundry/veil/api/client/render/framebuffer/FramebufferStack.class")) continue;
          var node = new ClassNode(); new org.objectweb.asm.ClassReader(jar.readAllBytes()).accept(node, 0);
          check(FramebufferSnapshotSupport.matches(node), "runtime compatibility guard admits installed Veil");
          var push = node.methods.stream().filter(m -> m.name.equals("push")).findFirst().orElseThrow();
          var constants = new ArrayList<Integer>(); boolean between = false;
          for (var insn : push.instructions) {
            if (insn instanceof MethodInsnNode call) {
              if (call.owner.equals("org/lwjgl/opengl/GL30C") && call.name.equals("glGetInteger")) {
                var previous = insn.getPrevious();
                while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                check(previous instanceof LdcInsnNode, "query has literal enum");
                constants.add((Integer) ((LdcInsnNode) previous).cst);
                between = constants.size() < 3;
              } else if (between) throw new AssertionError("new side effect between binding queries: " + call.owner + "." + call.name);
            }
          }
          check(constants.equals(List.of(36006, 36010, 36006)), "installed Veil snapshot queries expected aliases");
          for (var insn : push.instructions)
            if (insn instanceof MethodInsnNode call && call.name.equals("glGetInteger")) {
              push.instructions.insert(insn, new MethodInsnNode(184, "org/lwjgl/opengl/GL30C", "glBindFramebuffer", "(II)V", false));
              break;
            }
          check(!FramebufferSnapshotSupport.matches(node), "upstream state changes disable optimization");
          found = true; break;
        }
        check(found, "native snapshot bytecode verified");
      }
    }
    System.out.println("VeilFramebufferSnapshotCheck: draw/read separation, zero, invocation isolation and native target passed; disabled=" + disabled);
  }
}
