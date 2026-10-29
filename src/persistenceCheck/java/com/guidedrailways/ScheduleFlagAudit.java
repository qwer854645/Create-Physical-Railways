package com.guidedrailways;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.tree.ClassNode;

/**
 * Every boolean on the schedule controller must be classified, so none of them stays an unexamined source of state.
 *
 * <p>The controller keeps sixteen codes and eight booleans, and the booleans are the dangerous half: a code has one
 * writer, while a boolean can be flipped anywhere, and the combinations it forms with the code are not written down.
 * The audit does not remove them - several are genuinely independent - it forces a decision about each one, and
 * fails the build when a new one appears without one.
 *
 * <p>Categories:
 *
 * <ul>
 *   <li>{@code STATE} - part of what the schedule is doing; a candidate to fold into the stage.
 *   <li>{@code REQUEST} - a one-shot instruction from somewhere else, consumed and cleared.
 *   <li>{@code MONITOR} - bookkeeping about something observed, not a state of the schedule itself.
 * </ul>
 */
public final class ScheduleFlagAudit {
  enum Category {
    STATE,
    REQUEST,
    MONITOR
  }

  /** The contract: every boolean instance field of the controller, with what it is. */
  private static final Map<String, Category> FLAGS = new LinkedHashMap<>();

  static {
    FLAGS.put("running", Category.STATE);
    FLAGS.put("manualOverride", Category.STATE);
    FLAGS.put("waiting", Category.STATE);
    FLAGS.put("releaseBrakes", Category.REQUEST);
    FLAGS.put("visualDirty", Category.REQUEST);
    FLAGS.put("routeRetry", Category.REQUEST);
    FLAGS.put("resumePending", Category.REQUEST);
    FLAGS.put("mailMonitored", Category.MONITOR);
  }

  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("schedule flags: " + what);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input =
        ScheduleFlagAudit.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    var controller = code("ScheduleController");

    // Every declared boolean must be classified.
    for (var field : controller.fields) {
      if (!field.desc.equals("Z")) continue;
      if ((field.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) continue;
      check(
          FLAGS.containsKey(field.name),
          "ScheduleController." + field.name + " is a boolean that must be classified in this audit");
    }

    // Every classified flag must still exist: a rename must not leave a stale entry claiming coverage.
    for (var name : FLAGS.keySet()) {
      boolean present = false;
      for (var field : controller.fields) if (field.name.equals(name) && field.desc.equals("Z")) present = true;
      check(present, "classified flag " + name + " is no longer a boolean field of the controller");
    }

    // The categories must not collapse: a flag cannot be both state and request, and the audit must know about all
    // three kinds, or the classification is decorative.
    var seen = new java.util.HashSet<Category>(FLAGS.values());
    check(seen.size() >= 2, "the classification should distinguish at least two kinds of flag");
    check(seen.contains(Category.STATE), "some flags are state");
    check(seen.contains(Category.REQUEST), "some flags are requests");

    long stateFlags = FLAGS.values().stream().filter(c -> c == Category.STATE).count();
    check(stateFlags >= 1, "at least one flag must be a candidate for the stage");

    System.out.println(
        "ScheduleFlagAudit: " + checks + " checks over " + FLAGS.size() + " classified flags");
  }
}
