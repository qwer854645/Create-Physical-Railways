package com.guidedrailways;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/** A cargo snapshot belongs to these exact block entities, not just their old positions. */
final class StorageIdentities<K, V> {
  private final Map<K, WeakReference<V>> owners = new HashMap<>();
  private boolean validated, matching;
  private long validatedTick;

  void capture(K position, V owner) {
    owners.put(position, new WeakReference<>(owner));
    validated = false;
  }

  boolean matches(long tick, Function<K, V> current) {
    if (!validated || validatedTick != tick) {
      matching = matches(current);
      validatedTick = tick;
      validated = true;
    }
    return matching;
  }

  boolean matches(Function<K, V> current) {
    for (var entry : owners.entrySet()) {
      var owner = entry.getValue().get();
      if (owner == null || current.apply(entry.getKey()) != owner) return false;
    }
    return true;
  }
}
