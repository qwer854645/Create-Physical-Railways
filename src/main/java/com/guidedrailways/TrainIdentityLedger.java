package com.guidedrailways;

import java.util.*;

/**
 * Stable identity reconciler. Members are bogie-body UUIDs, not controllers or transient entities.
 */
public final class TrainIdentityLedger {
  public record Identity(UUID id, UUID anchor, Set<UUID> members) {
    public Identity {
      members = Set.copyOf(members);
    }
  }

  final Map<UUID, Identity> records = new HashMap<>();

  public Map<Set<UUID>, UUID> reconcile(List<Set<UUID>> groups) {
    return reconcile(groups, Set.copyOf(groups));
  }

  /**
   * Observe all loaded groups to distinguish splitting from unloading; only eligible groups start
   * identities.
   */
  public Map<Set<UUID>, UUID> reconcile(List<Set<UUID>> groups, Set<Set<UUID>> eligible) {
    var loaded = new HashSet<UUID>();
    groups.forEach(loaded::addAll);
    var original = List.copyOf(records.values());
    var anchors = new HashMap<UUID, Identity>();
    var owners = new HashMap<UUID, Identity>();
    var blocked = new HashSet<UUID>();
    for (var identity : original) {
      anchors.put(identity.anchor(), identity);
      for (var member : identity.members()) owners.put(member, identity);
      if (!loaded.containsAll(identity.members())) blocked.add(identity.id());
    }
    var result = new HashMap<Set<UUID>, UUID>();
    var updates = new HashMap<UUID, Identity>();
    var retired = new HashSet<UUID>();
    var claimed = new HashSet<UUID>();
    for (var members : groups) {
      if (members.isEmpty()) continue;
      // Defer the entire known identity while one of its saved parts is unavailable.
      if (members.stream()
          .map(owners::get)
          .filter(Objects::nonNull)
          .anyMatch(r -> blocked.contains(r.id()))) continue;
      var candidates =
          members.stream()
              .map(anchors::get)
              .filter(Objects::nonNull)
              .sorted(Comparator.comparing(Identity::id))
              .toList();
      if (candidates.isEmpty() && !eligible.contains(members)) {
        claimed.addAll(members);
        continue;
      }
      UUID id = candidates.isEmpty() ? UUID.randomUUID() : candidates.getFirst().id();
      UUID anchor =
          candidates.isEmpty() ? Collections.min(members) : candidates.getFirst().anchor();
      for (var candidate : candidates) if (!candidate.id().equals(id)) retired.add(candidate.id());
      updates.put(id, new Identity(id, anchor, members));
      claimed.addAll(members);
      result.put(Set.copyOf(members), id);
    }
    for (var old : original) {
      if (retired.contains(old.id())) records.remove(old.id());
      else if (!updates.containsKey(old.id()) && !Collections.disjoint(old.members(), claimed)) {
        var remaining = new HashSet<>(old.members());
        remaining.removeAll(claimed);
        if (remaining.isEmpty()) records.remove(old.id());
        else records.put(old.id(), new Identity(old.id(), old.anchor(), remaining));
      }
    }
    records.putAll(updates);
    return Map.copyOf(result);
  }

  public void removed(UUID member) {
    for (var r : List.copyOf(records.values())) {
      if (!r.members().contains(member)) continue;
      var remaining = new HashSet<>(r.members());
      remaining.remove(member);
      if (remaining.isEmpty()) records.remove(r.id());
      else
        records.put(
            r.id(),
            new Identity(
                r.id(),
                r.anchor().equals(member) ? Collections.min(remaining) : r.anchor(),
                remaining));
    }
  }
}
