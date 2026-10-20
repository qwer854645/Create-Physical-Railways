package com.guidedrailways;

import java.util.*;

/** Live seated players only; an incomplete consist must never satisfy even a zero target. */
final class PassengerManifest {
  private final Set<UUID> bodies;
  private final Set<UUID> players = new HashSet<>();
  private final boolean complete;

  PassengerManifest(Set<UUID> bodies, boolean complete) {
    this.bodies = Set.copyOf(bodies);
    this.complete = complete;
  }

  void seated(UUID player, UUID body) {
    if (bodies.contains(body)) players.add(player);
  }

  int count() {
    return players.size();
  }

  boolean complete() {
    return complete;
  }

  Set<UUID> bodies() {
    return bodies;
  }

  boolean satisfies(int target, boolean atLeast) {
    return complete && (atLeast ? count() >= target : count() == target);
  }
}
