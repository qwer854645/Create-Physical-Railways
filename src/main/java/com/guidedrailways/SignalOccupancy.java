package com.guidedrailways;

import java.util.*;
import java.util.function.Predicate;

/** Server-thread ledger: occupation and short route claims have separate lifetimes. */
public final class SignalOccupancy {
  public record Claim(UUID owner, UUID boundary, long expires) {}

  private final Map<UUID, Set<UUID>> occupants = new HashMap<>();
  private final Map<UUID, Claim> claims = new HashMap<>();

  public void refresh(Map<UUID, Set<UUID>> groupsByOwner, Set<UUID> running, long tick) {
    occupants.clear();
    groupsByOwner.forEach(
        (owner, groups) ->
            groups.forEach(
                group -> occupants.computeIfAbsent(group, k -> new HashSet<>()).add(owner)));
    claims
        .entrySet()
        .removeIf(
            e ->
                e.getValue().expires < tick
                    || !running.contains(e.getValue().owner)
                    || occupants.getOrDefault(e.getKey(), Set.of()).contains(e.getValue().owner));
  }

  public boolean blocked(UUID group, UUID owner) {
    if (occupants.getOrDefault(group, Set.of()).stream().anyMatch(id -> !id.equals(owner)))
      return true;
    var claim = claims.get(group);
    return claim != null && !claim.owner.equals(owner);
  }

  public boolean nativeBlocked(UUID group, UUID boundary) {
    if (!occupants.getOrDefault(group, Set.of()).isEmpty()) return true;
    var claim = claims.get(group);
    return claim != null && (boundary == null || !boundary.equals(claim.boundary));
  }

  public boolean claim(
      UUID owner, Map<UUID, UUID> requested, long tick, Predicate<UUID> nativeBlocked) {
    for (var group : requested.keySet())
      if (blocked(group, owner) || nativeBlocked.test(group)) return false;
    requested.forEach(
        (group, boundary) -> claims.put(group, new Claim(owner, boundary, tick + 10)));
    return true;
  }
}
