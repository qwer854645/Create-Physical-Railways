package com.guidedrailways;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * Binds real stopped physical trains to native station queries; motion stays in the physical
 * backend.
 */
public final class StationPresence {
  private record Key(UUID train, UUID station) {}

  private record Arrival(
      GlobalStation station,
      PhysicalTrainProxy train,
      BlockPos pos,
      double distance,
      UUID bogie,
      int axle,
      int sign) {}

  private static final class State {
    final Map<Key, StationStopState> stops = new HashMap<>();
    Set<BlockPos> present = Set.of();
    final Map<UUID, Arrival> bindings = new HashMap<>();
  }

  private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

  public static boolean present(Level level, BlockPos pos) {
    return level instanceof ServerLevel server
        && STATES.containsKey(server)
        && STATES.get(server).present.contains(pos);
  }

  static void tick(ServerLevel level) {
    var state = STATES.computeIfAbsent(level, k -> new State());
    var arrivals = new HashMap<Key, Arrival>();
    var seen = new HashSet<Key>();
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var docked = new HashMap<UUID, Arrival>();
    for (var binding : state.bindings.values()) docked.put(binding.train.id, binding);
    for (var component : TrainConsists.get(level).groups()) {
      var group = component.units();
      if (group.isEmpty()) continue;
      var proxy = NativeTrainBridge.registered(level, component);
      if (proxy == null || proxy.derailed || proxy.graph == null) continue;
      var owner = group.getFirst().driver;
      if (owner == null) continue;
      var links = component.links();
      if (group.stream()
          .anyMatch(
              u ->
                  u.detached
                      || u.bogie.isRemoved()
                      || u.carrier.isRemoved()
                      || u.mount.isRemoved())) continue;
      VehiclePhysics.Unit head;
      int end, sign;
      var previous = docked.get(proxy.id);
      if (previous != null) {
        // Keep the physical arrival reference while parked. Changing the reverser or operator
        // must not move the "front" to the other end and create a false departure.
        head =
            group.stream()
                .filter(u -> u.bogie.getUniqueId().equals(previous.bogie))
                .findFirst()
                .orElse(null);
        if (head == null) continue;
        end = previous.axle;
        sign = previous.sign;
      } else {
        int direction =
            Math.abs(owner.actual) > .12
                ? (owner.actual < 0 ? -1 : 1)
                : owner.direction == 0 ? 1 : owner.direction;
        var basis = AutomaticTrains.basis(owner, group, links, direction);
        if (basis == null) continue;
        head = basis.head();
        end = basis.end();
        sign = basis.signs().get(head.bogie.getUniqueId());
      }
      var heading =
          new Vector3d(0, 0, 1).rotate(pipe.readPose(head.bogie, new Pose3d()).orientation());
      var sample =
          TrackNetwork.nearest(
              level,
              VehiclePhysics.world(head.bogie, head.axle(end)),
              heading,
              head.samples[end] == null ? null : head.samples[end].path(),
              1,
              head.mount.gauge);
      if (sample == null) continue;
      var path = sample.path();
      if (proxy.graph != path.network()) continue;
      boolean increasing = PathGeometry.tangent(path::at, sample.t()).dot(heading) * sign >= 0;
      int from = increasing ? path.a() : path.b(), to = increasing ? path.b() : path.a();
      var fromNode = path.network().getNode(from);
      var toNode = path.network().getNode(to);
      if (fromNode == null || toNode == null) continue;
      var edge = TrackNetwork.connections(path.network(), fromNode).get(toNode);
      if (edge == null) continue;
      double speed = 0;
      for (var u : group) {
        speed = Math.max(speed, pipe.getLinearVelocity(u.bogie, new Vector3d()).length());
        speed = Math.max(speed, pipe.getLinearVelocity(u.carrier, new Vector3d()).length());
      }
      for (var station : path.network().getPoints(EdgePointType.STATION)) {
        if (station.assembling
            || station.edgeLocation == null
            || !station.edgeLocation.getFirst().equals(fromNode.getLocation())
            || !station.edgeLocation.getSecond().equals(toNode.getLocation())
            || !station.canApproachFrom(toNode)
            || !level.dimension().equals(station.getBlockEntityDimension())) continue;
        var pos = station.getBlockEntityPos();
        if (pos == null
            || !level.hasChunkAt(pos)
            || !(level.getBlockEntity(pos) instanceof StationBlockEntity stationBlock)
            || stationBlock.getStation() != station
            || stationBlock.isAssembling()) continue;
        double distance =
            station.getLocationOn(edge)
                - (increasing ? sample.t() : 1 - sample.t()) * edge.getLength();
        if (Math.abs(distance) > 1) continue;
        var key = new Key(proxy.id, station.id);
        seen.add(key);
        if (state
            .stops
            .computeIfAbsent(key, k -> new StationStopState())
            .tick(distance, speed, true))
          arrivals.put(
              key,
              new Arrival(
                  station, proxy, pos.immutable(), distance, head.bogie.getUniqueId(), end, sign));
      }
    }
    state.stops.keySet().removeIf(k -> !seen.contains(k));
    reconcile(state, arrivals);
  }

  private static void reconcile(State state, Map<Key, Arrival> arrivals) {
    var candidates = new ArrayList<StationBindingSelection.Candidate>();
    var previous = new HashMap<UUID, UUID>();
    var blocked = new HashSet<UUID>();
    for (var binding : state.bindings.values()) previous.put(binding.station.id, binding.train.id);
    for (var arrival : arrivals.values()) {
      var nearest = arrival.station.getNearestTrain();
      var old = state.bindings.get(arrival.station.id);
      if (nearest != null && (old == null || nearest != old.train)) blocked.add(arrival.station.id);
      candidates.add(
          new StationBindingSelection.Candidate(
              arrival.train.id, arrival.station.id, arrival.distance));
    }
    var desired = StationBindingSelection.choose(candidates, previous, blocked);
    for (var old : List.copyOf(state.bindings.values())) {
      var next = arrivals.get(new Key(desired.get(old.station.id), old.station.id));
      if (next == null
          || next.station != old.station
          || next.train != old.train
          || old.station.getNearestTrain() != old.train
          || old.train.getCurrentStation() != old.station) {
        release(old);
        state.bindings.remove(old.station.id);
      }
    }
    for (var entry : desired.entrySet()) {
      var arrival = arrivals.get(new Key(entry.getValue(), entry.getKey()));
      if (state.bindings.containsKey(entry.getKey())) continue;
      if (arrival.station.assembling || arrival.station.getNearestTrain() != null) continue;
      // reserveFor alone does not mean arrival; both sides must refer to the same native station.
      arrival.station.reserveFor(arrival.train);
      if (arrival.station.getNearestTrain() != arrival.train) continue;
      arrival.train.station(arrival.station.id);
      state.bindings.put(arrival.station.id, arrival);
      NativeTrainBridge.sync(arrival.train);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[PhysicalStation] station={} train={} present=true",
              arrival.station.id,
              arrival.train.id);
    }
    refreshPresence(state);
  }

  private static void refreshPresence(State state) {
    state.present =
        state.bindings.values().stream()
            .map(Arrival::pos)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static void release(Arrival binding) {
    binding.station.trainDeparted(binding.train); // Cancels only this exact train's reservation.
    if (binding.station.id.equals(binding.train.currentStation)) binding.train.station(null);
    NativeTrainBridge.sync(binding.train);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[PhysicalStation] station={} train={} present=false",
            binding.station.id,
            binding.train.id);
  }

  static void releaseTrain(ServerLevel level, PhysicalTrainProxy train) {
    var state = STATES.get(level);
    if (state != null) {
      for (var binding : List.copyOf(state.bindings.values()))
        if (binding.train == train) {
          release(binding);
          state.bindings.remove(binding.station.id);
        }
      refreshPresence(state);
    }
    var station = train.getCurrentStation();
    if (station != null) station.cancelReservation(train);
    train.station(null);
  }

  static void unload(ServerLevel level) {
    var state = STATES.remove(level);
    if (state != null) for (var binding : state.bindings.values()) release(binding);
  }
}
