package com.guidedrailways;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class IncrementalSnapshotCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var reads = new AtomicInteger();
    var snapshot =
        new IncrementalSnapshot<Integer>(
            () ->
                new Iterator<>() {
                  int index;

                  public boolean hasNext() {
                    return index < 100000;
                  }

                  public Integer next() {
                    reads.incrementAndGet();
                    return index++;
                  }
                });
    check(reads.get() == 0, "creating a scan cannot copy positions");
    while (!snapshot.ready()) {
      int before = reads.get();
      snapshot.advance();
      check(reads.get() - before <= 1, "one budget unit reads at most one position");
    }
    for (int i = 0; i < 100000; i++) check(snapshot.next() == i, "paged snapshot preserves order");
    check(!snapshot.hasNext(), "consumed snapshot releases its pages");
    var mutable = new ArrayList<>(List.of(1, 2, 3));
    var changing = new IncrementalSnapshot<Integer>(mutable::iterator);
    changing.advance();
    mutable.add(4);
    changing.advance();
    while (!changing.ready()) changing.advance();
    mutable.clear();
    for (int i = 1; i <= 4; i++)
      check(
          changing.next() == i,
          "mutation restarts capture without duplicates; processing is detached from source");
    check(!changing.hasNext(), "restart discarded partial capture");
    var empty = new IncrementalSnapshot<Integer>(Collections::emptyIterator);
    empty.advance();
    check(empty.ready() && !empty.hasNext(), "unloaded or empty chunk completes without work");
    System.out.println(
        "IncrementalSnapshotCheck: 100000 positions, per-step budget, page order, mutation restart and unload passed");
  }
}
