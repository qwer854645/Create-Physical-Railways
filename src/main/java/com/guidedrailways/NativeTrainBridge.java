package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

/** Maintains native query objects. Does not issue motion, schedule, station or signal commands. */
public final class NativeTrainBridge {
  private static final Map<ServerLevel, Set<UUID>> WARNED = new WeakHashMap<>();
  private static final Map<ServerLevel, Map<UUID, PhysicalTrainProxy>> LIVE = new WeakHashMap<>();

  public static Map<UUID, Train> nativeOnly(Map<UUID, Train> trains) {
    if (trains.values().stream().noneMatch(PhysicalTrainProxy.class::isInstance)) return trains;
    // Collision queries are hot: use a filtered view, not a copied registry per carriage.
    return Collections.unmodifiableMap(
        com.google.common.collect.Maps.filterValues(
            trains, train -> !(train instanceof PhysicalTrainProxy)));
  }

  static void observe(SubLevelContainer container) {
    container.addObserver(
        new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
          @Override
          public void onSubLevelRemoved(
              dev.ryanhcode.sable.sublevel.SubLevel body, SubLevelRemovalReason reason) {
            if (reason != SubLevelRemovalReason.REMOVED
                || !(body.getLevel() instanceof ServerLevel level)) return;
            var data = TrainIdentityData.get(level);
            var before = Map.copyOf(data.ledger.records);
            data.ledger.removed(body.getUniqueId());
            if (!before.equals(data.ledger.records)) data.setDirty();
          }
        });
  }

  static void tick(ServerLevel level) {
    if (level.getGameTime() % 5 != 0 || SubLevelPhysicsSystem.get(level) == null) return;
    var groups =
        TrainConsists.get(level).groups().stream()
            .filter(
                g ->
                    !g.units().isEmpty()
                        && g.bodies().values().stream()
                            .noneMatch(dev.ryanhcode.sable.sublevel.SubLevel::isRemoved)
                        && g.units().stream()
                            .noneMatch(u -> u.bogie.isRemoved() || u.mount.isRemoved())
                        && CouplerConnections.cargoComplete(level, g.members()))
            .toList();
    var data = TrainIdentityData.get(level);
    var before = Map.copyOf(data.ledger.records);
    var memberships =
        groups.stream()
            .map(
                g ->
                    g.units().stream()
                        .map(u -> u.bogie.getUniqueId())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
            .toList();
    var identities = data.ledger.reconcile(memberships);
    if (!before.equals(data.ledger.records)) data.setDirty();
    var live = LIVE.computeIfAbsent(level, l -> new HashMap<>());
    var seen = new HashSet<UUID>();
    var warned = WARNED.computeIfAbsent(level, l -> new HashSet<>());
    warned.retainAll(identities.values());
    for (int i = 0; i < groups.size(); i++) {
      var id = identities.get(memberships.get(i));
      if (id == null) continue;
      var snapshot = snapshot(level, id, groups.get(i));
      if (snapshot == null) {
        if (warned.add(id))
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
              .warn(
                  "[NativeTrainIdentity] query proxy deferred id={}; a linear consist with bogey-supported carriages is required",
                  id);
        continue;
      }
      warned.remove(id);
      seen.add(id);
      var proxy = live.get(id);
      boolean created = proxy == null;
      if (created) {
        proxy = new PhysicalTrainProxy(id);
        live.put(id, proxy);
      }
      proxy.apply(snapshot, Create.RAILWAYS.trackNetworks);
      var previous = Create.RAILWAYS.trains.get(id);
      if (previous != proxy) {
        if (previous != null)
          throw new IllegalStateException("Physical train identity collision: " + id);
        Create.RAILWAYS.addTrain(proxy);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .info(
                "[NativeTrainIdentity] registered id={} carriages={} dimension={}",
                id,
                proxy.carriages.size(),
                level.dimension().location());
      }
      if (created || level.getGameTime() % 10 == 0)
        PacketDistributor.sendToAllPlayers(new NativeTrainNetwork.Update(snapshot));
    }
    for (var id : List.copyOf(live.keySet())) if (!seen.contains(id)) remove(live, id);
  }

  private static void remove(Map<UUID, PhysicalTrainProxy> live, UUID id) {
    var proxy = live.remove(id);
    if (Create.RAILWAYS.trains.get(id) == proxy) Create.RAILWAYS.removeTrain(id);
    var removed = new CompoundTag();
    removed.putUUID("Id", id);
    removed.putBoolean("Removed", true);
    PacketDistributor.sendToAllPlayers(new NativeTrainNetwork.Update(removed));
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info("[NativeTrainIdentity] unregistered id={}", id);
  }

  static void unload(ServerLevel level) {
    WARNED.remove(level);
    var live = LIVE.remove(level);
    if (live != null) for (var id : List.copyOf(live.keySet())) remove(live, id);
  }

  static void login(net.minecraft.server.level.ServerPlayer player) {
    for (var live : LIVE.values())
      for (var proxy : live.values())
        PacketDistributor.sendToPlayer(player, new NativeTrainNetwork.Update(proxy.snapshot()));
  }

  private static CompoundTag snapshot(ServerLevel level, UUID id, TrainConsists.Group group) {
    var byBody = new HashMap<UUID, List<VehiclePhysics.Unit>>();
    for (var u : group.units())
      byBody.computeIfAbsent(u.carrier.getUniqueId(), k -> new ArrayList<>()).add(u);
    var adjacency = new TreeMap<UUID, Set<UUID>>();
    for (var body : group.members()) adjacency.put(body, new TreeSet<>());
    for (var link : group.links()) {
      adjacency.get(link.a().getUniqueId()).add(link.b().getUniqueId());
      adjacency.get(link.b().getUniqueId()).add(link.a().getUniqueId());
    }
    if (adjacency.values().stream().anyMatch(s -> s.size() > 2)) return null;
    var start =
        adjacency.keySet().stream()
            .filter(b -> adjacency.get(b).size() <= 1)
            .findFirst()
            .orElse(null);
    if (start == null) return null;
    var order = new ArrayList<UUID>();
    UUID current = start, previous = null;
    while (current != null) {
      if (order.contains(current)) return null;
      order.add(current);
      var back = previous;
      var next =
          adjacency.get(current).stream().filter(n -> !n.equals(back)).findFirst().orElse(null);
      previous = current;
      current = next;
    }
    if (order.size() != group.members().size()) return null;
    var centers = new HashMap<UUID, Vector3d>();
    for (var body : group.bodies().values())
      centers.put(body.getUniqueId(), body.boundingBox().center(new Vector3d()));
    var tag = new CompoundTag();
    tag.putUUID("Id", id);
    tag.putString("Dimension", level.dimension().location().toString());
    var carriages = new ListTag();
    UUID graph = null;
    boolean detached = false;
    double totalLength = 0, speed = 0;
    Vector3d lastBack = null;
    for (int index = 0; index < order.size(); index++) {
      var bodyId = order.get(index);
      var units = byBody.getOrDefault(bodyId, List.of());
      var body = group.bodies().get(bodyId);
      var center = centers.get(bodyId);
      var axis = new Vector3d(0, 0, 1).rotate(body.logicalPose().orientation());
      if (order.size() > 1) {
        var direction =
            index == 0
                ? new Vector3d(center).sub(centers.get(order.get(1)))
                : new Vector3d(centers.get(order.get(index - 1))).sub(center);
        if (direction.lengthSquared() > 1e-8 && axis.dot(direction) < 0) axis.negate();
      }
      var forward = axis;
      var sorted =
          units.stream()
              .sorted(
                  Comparator.comparingDouble(
                          (VehiclePhysics.Unit u) ->
                              VehiclePhysics.world(u.bogie, u.localCenter).dot(forward))
                      .reversed())
              .toList();
      var part = new CompoundTag();
      part.putUUID("Body", bodyId);
      if (!sorted.isEmpty()) {
        part.put("Front", bogey(sorted.getFirst(), forward));
        if (sorted.size() > 1) part.put("Rear", bogey(sorted.getLast(), forward));
      } else return null;
      for (var u : units) {
        detached |= u.detached;
        for (var sample : u.samples) {
          if (sample == null) {
            detached = true;
            continue;
          }
          if (graph == null) graph = sample.path().graph();
          else if (!graph.equals(sample.path().graph())) detached = true;
        }
      }
      var boxes = new ArrayList<dev.ryanhcode.sable.companion.math.BoundingBox3dc>();
      boxes.add(body.boundingBox());
      for (var unit : units) boxes.add(unit.bogie.boundingBox());
      double low = Double.POSITIVE_INFINITY, high = -low;
      for (var bounds : boxes)
        for (double x : new double[] {bounds.minX(), bounds.maxX()})
          for (double y : new double[] {bounds.minY(), bounds.maxY()})
            for (double z : new double[] {bounds.minZ(), bounds.maxZ()}) {
              double d = new Vector3d(x, y, z).sub(center).dot(forward);
              low = Math.min(low, d);
              high = Math.max(high, d);
            }
      var ahead = new Vector3d(center).fma(high, forward);
      var back = new Vector3d(center).fma(low, forward);
      vector(part, "Ahead", ahead);
      vector(part, "Back", back);
      totalLength += high - low;
      if (lastBack != null) totalLength += lastBack.distance(ahead);
      lastBack = back;
      if (index == 0)
        speed =
            SubLevelPhysicsSystem.get(level)
                .getPipeline()
                .getLinearVelocity(body, new Vector3d())
                .dot(forward);
      carriages.add(part);
    }
    if (graph != null && !detached) tag.putUUID("Graph", graph);
    tag.putBoolean("Derailed", detached || graph == null);
    tag.putDouble("Speed", speed);
    tag.putInt("Length", (int) Math.ceil(totalLength));
    tag.put("Carriages", carriages);
    return tag;
  }

  private static CompoundTag bogey(VehiclePhysics.Unit unit, Vector3d forward) {
    var tag = new CompoundTag();
    tag.putInt("Variant", unit.mount.variant.ordinal());
    tag.putString("Support", unit.bogie.getUniqueId().toString());
    vector(tag, "Center", VehiclePhysics.world(unit.bogie, unit.localCenter));
    var a = VehiclePhysics.world(unit.bogie, unit.axle(0));
    var b = VehiclePhysics.world(unit.bogie, unit.axle(1));
    int first = new Vector3d(a).sub(b).dot(forward) >= 0 ? 0 : 1;
    for (int i = 0; i < 2; i++) {
      var sample = unit.samples[i == 0 ? first : 1 - first];
      var point = new CompoundTag();
      if (sample != null && !unit.detached) {
        var path = sample.path();
        boolean reverse = sample.tangent().dot(forward) < 0;
        point.putInt("From", reverse ? path.b() : path.a());
        point.putInt("To", reverse ? path.a() : path.b());
        point.putDouble(
            "Offset", (reverse ? 1 - sample.t() : sample.t()) * path.edge().getLength());
      }
      tag.put("P" + i, point);
    }
    return tag;
  }

  private static void vector(CompoundTag tag, String key, Vector3d value) {
    var vector = new CompoundTag();
    vector.putDouble("X", value.x);
    vector.putDouble("Y", value.y);
    vector.putDouble("Z", value.z);
    tag.put(key, vector);
  }

  private NativeTrainBridge() {}
}
