package com.guidedrailways;

import java.util.*;
import java.util.function.Predicate;

/** Shared captures distinguish unreadable stale data from a discarded comparison baseline. */
final class ConsistCache<K, V> {
  private static final class Entry<K, V> {
    final Set<K> members;
    final V value;
    boolean valid = true;
    Object token = new Object();

    Entry(Set<K> members, V value) {
      this.members = Set.copyOf(members);
      this.value = value;
    }

    void invalidate() {
      if (!valid) return;
      valid = false;
      token = new Object();
    }
  }

  private final Map<K, Entry<K, V>> entries = new HashMap<>();

  V get(K member) {
    var entry = entries.get(member);
    return entry == null || !entry.valid ? null : entry.value;
  }

  V previous(K member) {
    var entry = entries.get(member);
    return entry == null ? null : entry.value;
  }

  Object token(K member) {
    var entry = entries.get(member);
    return entry == null ? null : entry.token;
  }

  void put(Set<K> members, V value) {
    discard(members);
    var entry = new Entry<>(members, value);
    for (var member : entry.members) entries.put(member, entry);
  }

  void invalidate(Collection<K> members) {
    for (var member : members) {
      var entry = entries.get(member);
      if (entry != null) entry.invalidate();
    }
  }

  void discard(Collection<K> members) {
    for (var member : members) {
      var entry = entries.get(member);
      if (entry != null) for (var alias : entry.members) entries.remove(alias, entry);
    }
  }

  void removeIf(Predicate<V> expired) {
    entries.values().removeIf(entry -> expired.test(entry.value));
  }
}
