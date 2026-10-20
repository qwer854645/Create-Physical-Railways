package com.guidedrailways;

import java.util.List;

public final class MoveBatchCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static final class Body {
    int position, velocity;

    Body(int p, int v) {
      position = p;
      velocity = v;
    }
  }

  private record Move(Body body, int before, int velocity, int target) {}

  public static void main(String[] args) {
    for (int failed = 0; failed < 3; failed++) {
      var bodies = List.of(new Body(10, 2), new Body(20, -3), new Body(30, 4));
      var moves =
          List.of(
              new Move(bodies.get(0), 10, 2, 100),
              new Move(bodies.get(1), 20, -3, 200),
              new Move(bodies.get(2), 30, 4, 300));
      final int fail = failed;
      int[] applied = {0};
      try {
        MoveBatch.commit(
            moves,
            m -> {
              if (m == moves.get(fail)) throw new IllegalArgumentException("missing rail");
            },
            m -> {
              applied[0]++;
              m.body.position = m.target;
            },
            m -> {
              m.body.position = m.before;
            });
        throw new AssertionError("validation should fail");
      } catch (IllegalArgumentException expected) {
      }
      check(applied[0] == 0, "even last-bogie validation failure must not move any body");
      for (var m : moves)
        check(
            m.body.position == m.before && m.body.velocity == m.velocity,
            "rejected plan preserves all positions and velocities");
      try {
        MoveBatch.commit(
            moves,
            m -> {},
            m -> {
              m.body.position = m.target;
              m.body.velocity = 0;
              if (m == moves.get(fail))
                throw new IllegalStateException("native apply failed midway");
            },
            m -> {
              m.body.position = m.before;
              m.body.velocity = m.velocity;
            });
        throw new AssertionError("apply should fail");
      } catch (IllegalStateException expected) {
      }
      for (var m : moves)
        check(
            m.body.position == m.before && m.body.velocity == m.velocity,
            "apply failure restores moved and partly applied bodies");
      MoveBatch.commit(
          moves,
          m -> {},
          m -> {
            m.body.position = m.target;
            m.body.velocity = 0;
          },
          m -> {
            throw new AssertionError("success must not roll back");
          });
      for (var m : moves)
        check(m.body.position == m.target && m.body.velocity == 0, "valid plan moves every body");
    }
    int[] restored = {0};
    try {
      MoveBatch.commit(
          List.of(0, 1, 2),
          m -> {},
          m -> {
            if (m == 2) throw new IllegalStateException("apply");
          },
          m -> {
            restored[0]++;
            if (m == 2) throw new IllegalArgumentException("restore");
          });
      throw new AssertionError("must preserve original failure");
    } catch (IllegalStateException e) {
      check(e.getSuppressed().length == 1, "rollback error is attached to original failure");
    }
    check(restored[0] == 3, "one rollback error must not prevent restoring remaining bodies");
    var blocks = new java.util.HashMap<Integer, String>();
    blocks.put(1, "air");
    blocks.put(2, "player block");
    var targets =
        List.of(
            new BlockAssembly.Target<>(1, "collision"), new BlockAssembly.Target<>(2, "collision"));
    check(
        BlockAssembly.plan(targets, blocks::get, "air"::equals).isEmpty(),
        "last occupied collision target rejects the complete layout without writes");
    check(
        blocks.get(1).equals("air") && blocks.get(2).equals("player block"),
        "preflight preserves unrelated blocks");
    blocks.put(2, "collision");
    var plan = BlockAssembly.plan(targets, blocks::get, "air"::equals).orElseThrow();
    check(plan.size() == 2, "unchanged targets remain in validation set");
    blocks.put(2, "changed after planning");
    int[] writes = {0};
    try {
      MoveBatch.commit(
          plan,
          e -> {
            if (!blocks.get(e.key()).equals(e.before()))
              throw new IllegalStateException("target changed");
          },
          e -> {
            writes[0]++;
            blocks.put(e.key(), e.after());
          },
          e -> blocks.put(e.key(), e.before()));
      throw new AssertionError("must reject changed target");
    } catch (IllegalStateException expected) {
    }
    check(
        writes[0] == 0 && blocks.get(1).equals("air"),
        "a stale unchanged target rejects before the first collision write");
    blocks.put(2, "collision");
    int[] completed = {0};
    var transaction = new java.util.ArrayList<>(plan);
    transaction.add(new BlockAssembly.Edit<>(null, null, null));
    try {
      MoveBatch.commit(
          transaction,
          e -> {},
          e -> {
            if (e.key() == null) throw new IllegalStateException("mass refresh failed");
            blocks.put(e.key(), e.after());
          },
          e -> {
            if (e.key() != null) blocks.put(e.key(), e.before());
          });
      completed[0]++;
      throw new AssertionError("must roll back refresh failure");
    } catch (IllegalStateException expected) {
    }
    check(
        completed[0] == 0 && blocks.get(1).equals("air") && blocks.get(2).equals("collision"),
        "mass refresh failure restores the layout and cannot mark assembly complete");
    System.out.println("MoveBatchCheck: " + checks + " assertions passed");
  }
}
