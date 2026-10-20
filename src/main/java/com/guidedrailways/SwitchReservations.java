package com.guidedrailways;

import java.util.*;

/** A consist owns a passage, including reverse traversal, until its last axle clears it. */
final class SwitchReservations {
  record Key(UUID graph, int node) {}

  private record Claim(UUID train, int from, int to, long expires) {}

  private final Map<Key, Claim> claims = new HashMap<>();

  boolean claim(Key key, UUID train, int from, int to, long now) {
    claims.entrySet().removeIf(e -> e.getValue().expires <= now);
    var old = claims.get(key);
    if (old != null && (!old.train.equals(train) || !same(old, from, to))) return false;
    claims.put(key, new Claim(train, from, to, now + 40));
    return true;
  }

  boolean owns(Key key, UUID train, int from, int to, long now) {
    var old = claims.get(key);
    return old != null && old.expires > now && old.train.equals(train) && same(old, from, to);
  }

  private static boolean same(Claim c, int from, int to) {
    return c.from == from && c.to == to || c.from == to && c.to == from;
  }
}
