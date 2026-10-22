package com.guidedrailways;

import java.util.ArrayList;
import java.util.List;

public final class ConstructionTransactionCheck {
  public static void main(String[] args) {
    for (int failAt = 0; failAt < 5; failAt++) {
      var live = new ArrayList<Integer>();
      var undone = new ArrayList<Integer>();
      try (var transaction = new ConstructionTransaction()) {
        for (int i = 0; i < 5; i++) {
          final int edit = i;
          transaction.undo(() -> { live.remove(Integer.valueOf(edit)); undone.add(edit); });
          live.add(i);
          if (i == failAt) throw new IllegalStateException("injected edit failure");
        }
      } catch (IllegalStateException expected) {
        if (!live.isEmpty()) throw new AssertionError("partially applied edit leaked");
        for (int i = 0; i < undone.size(); i++)
          if (undone.get(i) != failAt - i) throw new AssertionError("rollback order");
      }
    }
    var live = new ArrayList<>(List.of(1));
    try (var transaction = new ConstructionTransaction()) {
      transaction.undo(live::clear);
      transaction.commit();
    }
    if (!live.equals(List.of(1))) throw new AssertionError("commit rolled back");
    var undo = new ArrayList<Integer>();
    try (var transaction = new ConstructionTransaction()) {
      transaction.undo(() -> undo.add(1));
      transaction.undo(() -> { throw new IllegalArgumentException("rollback"); });
      throw new IllegalStateException("original");
    } catch (IllegalStateException expected) {
      if (expected.getSuppressed().length != 1 || !undo.equals(List.of(1)))
        throw new AssertionError("rollback must continue and preserve original failure");
    }
    System.out.println("ConstructionTransactionCheck: partial failures, reverse rollback, commit and cleanup failure passed");
  }
}
