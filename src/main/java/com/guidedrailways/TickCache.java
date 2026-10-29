package com.guidedrailways;

import java.util.HashMap;
import java.util.Map;

/** World-tick cache; sweep also removes abandoned keys and records from a rewound clock. */
final class TickCache<K, V> {
  private record Entry<V>(V value, long tick) {}
  private final long lifetime;
  private final Map<K, Entry<V>> entries = new HashMap<>();

  TickCache(long lifetime) {
    if (lifetime < 0) throw new IllegalArgumentException("Negative lifetime");
    this.lifetime = lifetime;
  }

  private boolean expired(Entry<V> entry, long now) {
    return now < entry.tick() || now - entry.tick() > lifetime;
  }

  V get(K key, long now) {
    var entry = entries.get(key);
    if (entry == null) return null;
    if (expired(entry, now)) {
      entries.remove(key);
      return null;
    }
    return entry.value();
  }

  void put(K key, V value, long now) {
    entries.put(key, new Entry<>(value, now));
  }

  void tick(long now) {
    entries.values().removeIf(entry -> expired(entry, now));
  }

  int size() {
    return entries.size();
  }
}
