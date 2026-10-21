package com.guidedrailways;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Cache filter decisions against the exact ordered carriage revision vector. */
public final class ObserverFilterCache {
  private List<Integer> versions = List.of();
  private final Map<Object, Boolean> matches = new IdentityHashMap<>();

  public boolean test(Object filter, List<Integer> versions, boolean ready, BooleanSupplier query) {
    if (!ready) {
      clear();
      return false;
    }
    if (!this.versions.equals(versions)) {
      clear();
      this.versions = List.copyOf(versions);
    }
    var old = matches.get(filter);
    if (old != null) return old;
    boolean result = query.getAsBoolean();
    if (matches.size() >= 256) matches.clear();
    matches.put(filter, result);
    return result;
  }

  public void clear() {
    versions = List.of();
    matches.clear();
  }
}
