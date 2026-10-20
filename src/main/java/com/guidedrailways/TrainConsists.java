package com.guidedrailways;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Shared tick snapshot for control, routing, signals, cargo and traction. */
final class TrainConsists {
  record Group(
      Set<UUID> members,
      List<VehiclePhysics.Unit> units,
      Map<UUID, ServerSubLevel> bodies,
      List<CouplerConnections.BodyLink> links) {
    double mass() {
      double mass = bodies.values().stream().mapToDouble(b -> b.getMassTracker().getMass()).sum();
      for (var unit : units) mass += unit.bogie.getMassTracker().getMass();
      return mass;
    }
  }

  record Snapshot(
      long tick,
      List<Group> groups,
      Map<UUID, Group> byBody,
      List<CouplerConnections.BodyLink> links) {
    Group group(UUID body) {
      return byBody.get(body);
    }
  }

  private static final Map<ServerLevel, Snapshot> CACHE = new WeakHashMap<>();
  private static final Map<ServerLevel, Set<UUID>> CHANGES = new WeakHashMap<>();

  private TrainConsists() {}

  static void observe(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
    container.addObserver(
        new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
          @Override
          public void onSubLevelRemoved(
              dev.ryanhcode.sable.sublevel.SubLevel body,
              dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
            // Both unloading and deletion invalidate runtime references, without resetting
            // schedules.
            if (body.getLevel() instanceof ServerLevel level) invalidate(level);
          }
        });
  }

  static Snapshot get(ServerLevel level) {
    var snapshot = CACHE.get(level);
    return snapshot != null && snapshot.tick() == level.getGameTime()
        ? snapshot
        : refresh(level, VehiclePhysics.cargoUnits(level));
  }

  static Snapshot refresh(ServerLevel level, Collection<VehiclePhysics.Unit> input) {
    var units =
        input.stream()
            .filter(
                u ->
                    !u.carrier.isRemoved()
                        && !u.bogie.isRemoved()
                        && !u.mount.isRemoved()
                        && u.lastSeen + 2 >= level.getGameTime())
            .toList();
    var links =
        CouplerConnections.bodyLinks(level).stream()
            .filter(link -> !link.a().isRemoved() && !link.b().isRemoved())
            .toList();
    var bodies = new HashMap<UUID, ServerSubLevel>();
    for (var unit : units) bodies.put(unit.carrier.getUniqueId(), unit.carrier);
    var edges = new ArrayList<ConsistGraph.Link>();
    for (var link : links) {
      bodies.put(link.a().getUniqueId(), link.a());
      bodies.put(link.b().getUniqueId(), link.b());
      edges.add(new ConsistGraph.Link(link.a().getUniqueId(), link.b().getUniqueId()));
    }
    var groups = new ArrayList<Group>();
    var byBody = new HashMap<UUID, Group>();
    var byCarrier = new HashMap<UUID, List<VehiclePhysics.Unit>>();
    for (var unit : units)
      byCarrier.computeIfAbsent(unit.carrier.getUniqueId(), k -> new ArrayList<>()).add(unit);
    var components = ConsistGraph.components(bodies.keySet(), edges);
    var linkGroups = ConsistGraph.bucket(components, links, link -> link.a().getUniqueId());
    int componentIndex = 0;
    for (var members : components) {
      var groupBodies = new HashMap<UUID, ServerSubLevel>();
      var groupUnits = new ArrayList<VehiclePhysics.Unit>();
      for (var id : members) {
        groupBodies.put(id, bodies.get(id));
        groupUnits.addAll(byCarrier.getOrDefault(id, List.of()));
      }
      var groupLinks = linkGroups.get(componentIndex++);
      var group = new Group(members, List.copyOf(groupUnits), Map.copyOf(groupBodies), groupLinks);
      groups.add(group);
      for (var id : members) byBody.put(id, group);
    }
    var snapshot =
        new Snapshot(level.getGameTime(), List.copyOf(groups), Map.copyOf(byBody), links);
    CACHE.put(level, snapshot);
    return snapshot;
  }

  static void invalidate(ServerLevel level) {
    CACHE.remove(level);
    CargoStorage.topologyChanged(level);
    ConsistRoutes.topologyChanged(level);
  }

  static void changed(ServerLevel level, UUID... bodies) {
    invalidate(level);
    var changed = CHANGES.computeIfAbsent(level, k -> new HashSet<>());
    Collections.addAll(changed, bodies);
  }

  static void applyChanges(ServerLevel level) {
    var changed = CHANGES.remove(level);
    if (changed != null && !changed.isEmpty()) {
      var affected = new HashSet<>(changed);
      var snapshot = get(level);
      for (var body : changed) {
        var group = snapshot.group(body);
        if (group != null) affected.addAll(group.members());
      }
      DriverControls.topologyChanged(level, affected);
    }
  }

  static void unload(ServerLevel level) {
    CACHE.remove(level);
    CHANGES.remove(level);
  }
}
