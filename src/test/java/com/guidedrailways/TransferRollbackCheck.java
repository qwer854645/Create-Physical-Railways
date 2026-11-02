package com.guidedrailways;

import java.util.*;

/** Inject failures before/after destination deletion and source restoration in production rollback. */
public final class TransferRollbackCheck {
  private static int checks;
  private static void check(boolean result, String message) {
    checks++;
    if (!result) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    var ids = List.of(1, 2, 3);
    for (int mode = 0; mode <= 5; mode++) {
      final int fault = mode;
      var source = new HashMap<Integer, Integer>();
      var target = new HashMap<Integer, Integer>();
      source.put(3, 17); // A source member whose removal never happened.
      source.put(99, 23); target.put(98, 29); // Unrelated owners.
      target.put(1, 17); target.put(2, 17);
      var failures = new ArrayList<RuntimeException>();
      boolean complete = TransferRollback.restore(ids, source::containsKey,
          key -> {
            if (fault == 5 && key == 1) throw new IllegalStateException("unavailable target");
            return target.containsKey(key);
          }, key -> {
            if (key == 1 && fault == 1) throw new IllegalStateException("remove before write");
            target.remove(key);
            if (key == 1 && fault == 2) throw new IllegalStateException("remove after write");
          }, key -> {
            check(!target.containsKey(key), "cannot restore a second owner while the destination exists");
            if (key == 1 && fault == 3) throw new IllegalStateException("restore before write");
            check(!source.containsKey(key), "cannot overwrite an already live source");
            source.put(key, 17);
            if (key == 1 && fault == 4) throw new IllegalStateException("restore after write");
          }, failures::add);
      check(complete == (mode == 0), "failures cannot be reported as completed recovery");
      check(source.get(2) == 17 && source.get(3) == 17, "healthy members recover despite another member's failure");
      check(source.get(99) == 23 && target.get(98) == 29, "unrelated inventories stay untouched");
      for (int id : ids)
        check(source.getOrDefault(id, 0) + target.getOrDefault(id, 0) <= 17,
            "failure at every mutation stage never duplicates cargo");
      if (mode != 3) check(source.getOrDefault(1, 0) + target.getOrDefault(1, 0) == 17,
          "cargo retains one owner unless source restoration itself fails before writing");
      check(failures.isEmpty() == (mode == 0), "diagnostics retain failures for manual recovery");
    }
    System.out.println("TransferRollbackCheck: " + checks + " rollback, ownership and injected failure checks passed");
  }
}
