package com.guidedrailways;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.*;

/** Only optimize the known read-only sequence; upstream changes disable the experiment. */
public final class FramebufferSnapshotSupport {
  private FramebufferSnapshotSupport() {}

  public static boolean matches(ClassNode node) {
    for (var method : node.methods) {
      if (!method.name.equals("push") || !method.desc.equals("(Lnet/minecraft/resources/ResourceLocation;)V")) continue;
      var queries = new ArrayList<Integer>();
      boolean between = false;
      for (var insn : method.instructions) {
        if (insn instanceof MethodInsnNode call) {
          if (call.owner.equals("org/lwjgl/opengl/GL30C") && call.name.equals("glGetInteger")
              && call.desc.equals("(I)I")) {
            var previous = insn.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            if (!(previous instanceof LdcInsnNode literal) || !(literal.cst instanceof Integer query)) return false;
            queries.add(query);
            between = queries.size() < 3;
          } else if (between) return false;
        }
        // Unknown side effects within the snapshot must not be hidden by the optimization.
        if (between && (insn instanceof InvokeDynamicInsnNode || insn instanceof JumpInsnNode
            || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode
            || insn instanceof FieldInsnNode field && (field.getOpcode() == org.objectweb.asm.Opcodes.PUTSTATIC
                || field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD)))
          return false;
      }
      return queries.equals(List.of(36006, 36010, 36006));
    }
    return false;
  }
}
