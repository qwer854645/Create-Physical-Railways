package com.guidedrailways;

import java.util.*;
import java.util.function.Predicate;

/** One cached value has several body aliases; invalidating any member expires all of them. */
final class ConsistCache<K, V> {
  private record Entry<K, V>(Set<K> members, V value) {}

  private final Map<K, Entry<K, V>> entries = new HashMap<>();

  V get(K member) {
    var entry = entries.get(member);
    return entry == null ? null : entry.value();
  }

  Object token(K member) {
    return entries.get(member);
  }

  void put(Set<K> members, V value) {
    invalidate(members);
    var entry = new Entry<>(Set.copyOf(members), value);
    for (var member : members) entries.put(member, entry);
  }

  void invalidate(Collection<K> members) {
    for (var member : members) {
      var entry = entries.get(member);
      if (entry != null) for (var alias : entry.members()) entries.remove(alias, entry);
    }
  }

  void removeIf(Predicate<V> expired) {
    entries.values().removeIf(entry -> expired.test(entry.value()));
  }
}
