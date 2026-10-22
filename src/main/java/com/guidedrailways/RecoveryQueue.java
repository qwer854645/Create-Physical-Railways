package com.guidedrailways;

import java.util.LinkedHashSet;
import java.util.function.Predicate;

/** Deduplicated, fair recovery work; incomplete entries return to the tail. */
final class RecoveryQueue<K> {
  private final LinkedHashSet<K> pending = new LinkedHashSet<>();

  void add(K key) {
    pending.add(key);
  }

  void remove(K key) {
    pending.remove(key);
  }

  void clear() {
    pending.clear();
  }

  int drain(int budget, Predicate<K> finished) {
    int count = 0;
    while (count < budget && !pending.isEmpty()) {
      var iterator = pending.iterator();
      var key = iterator.next();
      iterator.remove();
      boolean complete = false;
      try {
        complete = finished.test(key);
      } finally {
        if (!complete) pending.add(key);
      }
      count++;
    }
    return count;
  }
}
