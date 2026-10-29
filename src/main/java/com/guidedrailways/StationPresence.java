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

  private record Reference(VehiclePhysics.Unit head, int axle, int sign) {}

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

  /** Also consult the native association while the debounced presence cache is updating. */
  public static boolean blocksAssembly(StationBlockEntity block) {
    var station = block.getStation();
    return (station != null && station.getNearestTrain() instanceof PhysicalTrainProxy)
        || present(block.getLevel(), block.getBlockPos());
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
      double speed = 0;
      for (var u : group) {
        speed = Math.max(speed, pipe.getLinearVelocity(u.bogie, new Vector3d()).length());
        speed = Math.max(speed, pipe.getLinearVelocity(u.carrier, new Vector3d()).length());
      }
      if (speed > .2) continue;
      var references = new ArrayList<Reference>();
      var previous = docked.get(proxy.id);
      if (previous != null) {
        // While parked, changing the reverser must not move the arrival reference.
        var head =
            group.stream()
                .filter(u -> u.bogie.getUniqueId().equals(previous.bogie))
                .findFirst()
                .orElse(null);
        if (head == null) continue;
        references.add(new Reference(head, previous.axle, previous.sign));
      } else {
        // Rebuild from actual geometry after loading. The saved reverser may point away
        // from the end that arrived, so inspect both ends without trusting schedule state.
        for (int direction : new int[] {1, -1}) {
          var basis = AutomaticTrains.basis(owner, group, links, direction);
          if (basis == null) continue;
          references.add(
              new Reference(
                  basis.head(), basis.end(), basis.signs().get(basis.head().bogie.getUniqueId())));
        }
      }
      var candidates = new HashMap<Key, Arrival>();
      for (var reference : references) sample(level, proxy, reference, candidates);
      for (var entry : candidates.entrySet()) {
        seen.add(entry.getKey());
        if (state
            .stops
            .computeIfAbsent(entry.getKey(), k -> new StationStopState())
            .tick(entry.getValue().distance, speed, true))
          arrivals.put(entry.getKey(), entry.getValue());
      }
    }
    state.stops.keySet().removeIf(k -> !seen.contains(k));
    reconcile(level, state, arrivals);
  }

  private static void sample(
      ServerLevel level,
      PhysicalTrainProxy proxy,
      Reference reference,
      Map<Key, Arrival> candidates) {
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var head = reference.head;
    int end = reference.axle, sign = reference.sign;
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
    if (sample == null) return;
    var path = sample.path();
    if (proxy.graph != path.network()) return;
    boolean increasing = PathGeometry.tangent(path::at, sample.t()).dot(heading) * sign >= 0;
    int from = increasing ? path.a() : path.b(), to = increasing ? path.b() : path.a();
    var fromNode = path.network().getNode(from);
    var toNode = path.network().getNode(to);
    if (fromNode == null || toNode == null) return;
    var edge = TrackNetwork.connections(path.network(), fromNode).get(toNode);
    if (edge == null) return;
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
      var arrival =
          new Arrival(
              station, proxy, pos.immutable(), distance, head.bogie.getUniqueId(), end, sign);
      candidates.merge(
          key, arrival, (a, b) -> Math.abs(a.distance) <= Math.abs(b.distance) ? a : b);
    }
  }


  /**
   * True while the station's block entity still exists.
   *
   * <p>A station can be broken while a train stands at it. The binding then still names the global station,
   * and calling into one that no longer has a block behind it touches removed state; the train side still has
   * to be released, because otherwise the train keeps a station that no longer exists.
   */
  private static boolean live(ServerLevel level, GlobalStation station) {
    return station.blockEntityPos != null
        && level.getBlockEntity(station.blockEntityPos)
            instanceof com.simibubi.create.content.trains.station.StationBlockEntity;
  }

  private static void reconcile(ServerLevel level, State state, Map<Key, Arrival> arrivals) {
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
      if (!live(level, old.station)) {
        // Nothing can arrive at a station whose block is gone, whatever the selection would prefer.
        release(level, old);
        state.bindings.remove(old.station.id);
        continue;
      }
      var next = arrivals.get(new Key(desired.get(old.station.id), old.station.id));
      if (next == null
          || next.station != old.station
          || next.train != old.train
          || old.station.getNearestTrain() != old.train
          || old.train.getCurrentStation() != old.station) {
        release(level, old);
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

  private static void release(ServerLevel level, Arrival binding) {
    boolean broken = binding.station.blockEntityPos != null && !live(level, binding.station);
    if (broken)
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info("[StationGone] station={} train={}", binding.station.id, binding.train.id);
    // The station block can be broken while a train is standing at it, and then this binding points at a
    // block entity that has already been removed from the level. Its own bookkeeping is meaningless by
    // then, but the train side must still be cleared, or the train keeps a station that no longer exists.
    if (live(level, binding.station))
      binding.station.trainDeparted(binding.train); // Cancels only this exact train's reservation.
    if (binding.station.id.equals(binding.train.currentStation)) binding.train.station(null);
    NativeTrainBridge.sync(binding.train);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[PhysicalStation] station={} train={} present=false",
            binding.station.id,
            binding.train.id);
    if (broken) notifyBrokenStation(level, binding);
  }

  /**
   * Tells the players around a broken station what happened to their train.
   *
   * <p>Without this the train simply stopped being "at a station" and nothing said why, which is
   * indistinguishable from a train that is stuck.
   */
  private static void notifyBrokenStation(ServerLevel level, Arrival binding) {
    var message =
        net.minecraft.network.chat.Component.translatable("guidedrailways.status.station_gone");
    for (var player : level.players())
      if (player.distanceToSqr(
              binding.pos.getX() + .5, binding.pos.getY() + .5, binding.pos.getZ() + .5)
          < 64 * 64) player.displayClientMessage(message, false);
  }

  static void releaseTrain(ServerLevel level, PhysicalTrainProxy train) {
    var state = STATES.get(level);
    if (state != null) {
      for (var binding : List.copyOf(state.bindings.values()))
        if (binding.train == train) {
          release(level, binding);
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
    if (state != null) for (var binding : state.bindings.values()) release(level, binding);
  }
}
