package com.guidedrailways;

import java.util.*;
import java.util.function.Supplier;

/** Copy a mutable source one entry per step, into fixed-size pages without bulk resizing. */
final class IncrementalSnapshot<T> {
  private static final int PAGE_SIZE = 128;
  private final Supplier<Iterator<T>> source;
  private Iterator<T> iterator;
  private Deque<ArrayDeque<T>> pages = new LinkedList<>();
  private ArrayDeque<T> tail;
  private boolean ready;

  IncrementalSnapshot(Supplier<Iterator<T>> source) {
    this.source = source;
  }

  boolean ready() {
    return ready;
  }

  void advance() {
    if (ready) return;
    try {
      if (iterator == null) iterator = source.get();
      if (!iterator.hasNext()) {
        ready = true;
        iterator = null;
        return;
      }
      var value = iterator.next();
      if (tail == null || tail.size() == PAGE_SIZE) {
        tail = new ArrayDeque<>(PAGE_SIZE);
        pages.addLast(tail);
      }
      tail.addLast(value);
    } catch (ConcurrentModificationException changed) {
      iterator = null;
      pages = new LinkedList<>();
      tail = null;
    }
  }

  boolean hasNext() {
    return !pages.isEmpty();
  }

  T next() {
    if (!ready) throw new IllegalStateException("Snapshot is still being captured");
    var page = pages.getFirst();
    var value = page.removeFirst();
    if (page.isEmpty()) {
      pages.removeFirst();
      if (pages.isEmpty()) tail = null;
    }
    return value;
  }
}
