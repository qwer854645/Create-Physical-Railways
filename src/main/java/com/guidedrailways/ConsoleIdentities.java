package com.guidedrailways;

import java.util.*;
import java.util.function.Predicate;

/** A live console owns its persisted ID; a simultaneously loaded copy receives a fresh one. */
final class ConsoleIdentities<T> {
  private final Map<UUID, T> owners = new HashMap<>();
  private final Map<T, UUID> identities = new IdentityHashMap<>();

  UUID claim(UUID requested, T console, Predicate<T> live) {
    var previous = identities.get(console);
    if (previous != null && !previous.equals(requested)) release(console);
    var owner = owners.get(requested);
    if (owner != null && owner != console && live.test(owner)) {
      do {
        requested = UUID.randomUUID();
      } while (owners.containsKey(requested));
    } else if (owner != null && owner != console) release(owner);
    owners.put(requested, console);
    identities.put(console, requested);
    return requested;
  }

  void release(T console) {
    var id = identities.remove(console);
    if (id != null) owners.remove(id, console);
  }
}
