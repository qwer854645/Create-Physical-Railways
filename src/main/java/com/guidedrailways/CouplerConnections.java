package com.guidedrailways;

import static com.guidedrailways.CouplerCoordinates.*;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** One native radial spring/limit per pair; both mounting sockets swivel freely. */
public final class CouplerConnections {
  private record Pending(UUID endpoint, long expires) {}

  private static final class State {
    final Map<UUID, CouplerEndpoint> endpoints = new HashMap<>();
    final Map<UUID, CouplerJoint> live = new HashMap<>();
    final Map<UUID, Pending> pending = new HashMap<>();
    final Set<UUID> warned = new HashSet<>();
    final Map<UUID, String> waiting = new HashMap<>();
  }

  private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

  private CouplerConnections() {}

  private static State state(ServerLevel level) {
    return STATES.computeIfAbsent(level, l -> new State());
  }

  static void register(ServerLevel level, CouplerEndpoint e) {
    if (!loaded(level, e)) return;
    if (!CouplerTransfers.restore(level, e)) return;
    var data = CouplerSavedData.get(level);
    var s = state(level);
    var previous = s.endpoints.get(e.endpointId);
    if ((previous != null && previous != e && loaded(level, previous))
        || !data.claim(e.endpointId, e.getBlockPos())) {
      e.endpointId = UUID.randomUUID();
      e.hasGeometry = false;
      e.restLength = -1;
      e.hardwareFrame = null;
      e.visualPeer(null);
      e.setChanged();
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("Cloned coupler assigned a new endpoint ID at {}", e.getBlockPos());
    }
    var frame =
        String.valueOf(RailwayHardware.kind(e))
            + ":"
            + e.getBlockState().getValue(CouplerBlock.FACING);
    var flip =
        e.getBlockState().getProperties().stream()
            .filter(p -> p.getName().equals("upside_down"))
            .findFirst();
    if (flip.isPresent()) frame += ":" + e.getBlockState().getValue(flip.get());
    if (e.hardwareFrame != null && !e.hardwareFrame.equals(frame))
      disconnect(level, e.endpointId, "hardware_changed");
    e.hardwareFrame = frame;
    s.endpoints.put(e.endpointId, e);
    if (e.visualConnected && data.links.forEndpoint(e.endpointId) == null) e.visualPeer(null);
  }

  /** Incremental materialisation only while a saved pair lacks a loaded endpoint. */
  static void restoreLoaded(ServerLevel level) {
    var data = CouplerSavedData.get(level);
    var s = state(level);
    boolean unresolved = false;
    for (var link : data.links.all())
      if (!loaded(level, s.endpoints.get(link.a())) || !loaded(level, s.endpoints.get(link.b()))) {
        unresolved = true;
        break;
      }
    CouplerDiscovery.tick(level, unresolved);
  }

  private static void waiting(State s, CouplingLinks.Link link, String reason) {
    if (!reason.equals(s.waiting.put(link.id(), reason)))
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[CouplerRestore] waiting link={} reason={} endpoints={}/{}",
              link.id(),
              reason,
              link.a(),
              link.b());
  }

  static void unloaded(ServerLevel level, CouplerEndpoint e) {
    var s = STATES.get(level);
    if (s != null && s.endpoints.get(e.endpointId) == e) suspend(level, e.endpointId);
  }

  static void suspend(ServerLevel level, UUID endpoint) {
    var s = STATES.get(level);
    if (s == null) return;
    s.endpoints.remove(endpoint);
    // Unloading a plot must not send block updates into a holder being removed.
    // Keep saved links; endpoint registration will rebuild the joint and visuals.
    var link = CouplerSavedData.get(level).links.forEndpoint(endpoint);
    if (link != null) {
      var old = s.live.remove(link.id());
      if (old != null) {
        old.close(false);
        TrainConsists.invalidate(level);
      }
    }
  }

  private static void close(State s, UUID id) {
    var old = s.live.remove(id);
    if (old != null) {
      old.close();
      TrainConsists.invalidate(old.a.getLevel());
    }
  }

  private static void disconnect(ServerLevel level, UUID endpoint, String reason) {
    var data = CouplerSavedData.get(level);
    var link = data.links.forEndpoint(endpoint);
    if (link == null) return;
    var s = state(level);
    close(s, link.id());
    data.remove(link.id());
    s.warned.remove(link.id());
    s.waiting.remove(link.id());
    for (var id : List.of(link.a(), link.b())) {
      var e = s.endpoints.get(id);
      if (e != null) {
        e.hasGeometry = false;
        e.setChanged();
        e.visualPeer(null);
        var carrier = body(level, e);
        if (carrier != null) TrainConsists.changed(level, carrier.getUniqueId());
      }
    }
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info("[Coupler] disconnected link={} reason={}", link.id(), reason);
  }

  static void destroyed(ServerLevel level, CouplerEndpoint e) {
    if (CouplerTransfers.moving(level, e)) return;
    if (!CouplerSavedData.get(level).owns(e.endpointId, e.getBlockPos())) return;
    disconnect(level, e.endpointId, "removed");
    suspend(level, e.endpointId);
    state(level).pending.values().removeIf(p -> p.endpoint.equals(e.endpointId));
  }

  static void link(ServerLevel level, CouplerEndpoint e, ServerPlayer player) {
    register(level, e);
    if (state(level).endpoints.get(e.endpointId) != e || !loaded(level, e)) return;
    boolean unlink = connected(level, e);
    var s = state(level);
    var data = CouplerSavedData.get(level);
    if (unlink) {
      s.pending.remove(player.getUUID());
      if (data.links.forEndpoint(e.endpointId) == null)
        player.displayClientMessage(
            Component.translatable("message.guidedrailways.coupler.not_connected"), true);
      else {
        disconnect(level, e.endpointId, "link_tool");
        player.displayClientMessage(
            Component.translatable("message.guidedrailways.coupler.disconnected"), true);
      }
      return;
    }
    if (data.links.forEndpoint(e.endpointId) != null) {
      player.displayClientMessage(
          Component.translatable("message.guidedrailways.coupler.already_connected_hint"), true);
      return;
    }
    var pending = s.pending.get(player.getUUID());
    if (pending == null || pending.expires < level.getGameTime()) {
      if (body(level, e) == null)
        throw new IllegalArgumentException("message.guidedrailways.coupler.install");
      s.pending.put(player.getUUID(), new Pending(e.endpointId, level.getGameTime() + 600));
      player.displayClientMessage(
          Component.translatable("message.guidedrailways.coupler.selected"), true);
      return;
    }
    if (pending.endpoint.equals(e.endpointId)) {
      s.pending.remove(player.getUUID());
      player.displayClientMessage(
          Component.translatable("message.guidedrailways.coupler.cancelled"), true);
      return;
    }
    var first = s.endpoints.get(pending.endpoint);
    if (!loaded(level, first)) {
      s.pending.remove(player.getUUID());
      throw new IllegalArgumentException("message.guidedrailways.coupler.unloaded");
    }
    if (!level.mayInteract(player, first.getBlockPos()))
      throw new IllegalArgumentException("message.guidedrailways.coupler.denied");
    connect(level, first, e);
    s.pending.remove(player.getUUID());
    player.displayClientMessage(
        Component.translatable(
            RailwayHardware.kind(e) == CouplerKind.STANDARD
                ? "message.guidedrailways.coupler.connected"
                : "message.guidedrailways.coupler.connecting"),
        true);
  }

  private static void connect(ServerLevel level, CouplerEndpoint a, CouplerEndpoint b) {
    if (!CouplerKind.compatible(RailwayHardware.kind(a), RailwayHardware.kind(b)))
      throw new IllegalArgumentException("message.guidedrailways.coupler.type_mismatch");
    var data = CouplerSavedData.get(level);
    if (data.links.forEndpoint(a.endpointId) != null
        || data.links.forEndpoint(b.endpointId) != null)
      throw new IllegalArgumentException("message.guidedrailways.coupler.occupied");
    var ba = body(level, a);
    var bb = body(level, b);
    if (ba == null || bb == null)
      throw new IllegalArgumentException("message.guidedrailways.coupler.install_both");
    if (ba == bb) throw new IllegalArgumentException("message.guidedrailways.coupler.same_body");
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    if (!CouplerGeometry.slowEnough(
        pipe.getLinearVelocity(ba, new Vector3d()),
        pipe.getLinearVelocity(bb, new Vector3d()),
        pipe.getAngularVelocity(ba, new Vector3d()),
        pipe.getAngularVelocity(bb, new Vector3d())))
      throw new IllegalArgumentException("message.guidedrailways.coupler.too_fast");
    var frame = engagement(level, a, b, ba, bb);
    var kind = RailwayHardware.kind(a);
    var socketA = socket(a);
    var socketB = socket(b);
    double length =
        CouplerMechanism.nominalLength(
            kind,
            CouplerGeometry.nominalLength(
                RailwayHardware.pivot(a).distance(RailwayHardware.tip(a)),
                RailwayHardware.pivot(b).distance(RailwayHardware.tip(b))));
    length = CouplerBufferSpacing.length(level, a, b, ba, bb, length);
    a.geometry(socketA.sub(blockOrigin(a)), localFrame(ba, frame), length);
    b.geometry(socketB.sub(blockOrigin(b)), localFrame(bb, frame), length);
    a.visualPeer(b);
    b.visualPeer(a);
    var link = new CouplingLinks.Link(UUID.randomUUID(), a.endpointId, b.endpointId);
    data.add(link, a.getBlockPos(), b.getBlockPos());
    try {
      reconcile(level, link);
      if (CouplerMechanism.carries(kind, a.hardwareProgress(0))
          && !state(level).live.containsKey(link.id()))
        throw new IllegalStateException("message.guidedrailways.coupler.failed");
    } catch (RuntimeException error) {
      disconnect(level, a.endpointId, "creation_failed");
      throw error;
    }
    TrainConsists.changed(level, ba.getUniqueId(), bb.getUniqueId());
  }

  private static Quaterniond engagement(
      ServerLevel level,
      CouplerEndpoint a,
      CouplerEndpoint b,
      ServerSubLevel ba,
      ServerSubLevel bb) {
    var geometry =
        CouplerGeometry.engage(
            VehiclePhysics.world(ba, head(a)),
            facing(ba, a),
            up(ba),
            VehiclePhysics.world(bb, head(b)),
            facing(bb, b),
            up(bb));
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var kind = RailwayHardware.kind(a);
    var profile = CouplerMechanism.profile(kind);
    double gap = VehiclePhysics.world(ba, head(a)).distance(VehiclePhysics.world(bb, head(b)));
    if (gap > profile.reach())
      throw new IllegalArgumentException("message.guidedrailways.coupler.approach");
    if (kind != CouplerKind.STANDARD) {
      if (facing(ba, a).dot(facing(bb, b)) > -.3)
        throw new IllegalArgumentException("message.guidedrailways.coupler.align");
      if (pipe.getLinearVelocity(ba, new Vector3d()).length() > profile.maxSpeed()
          || pipe.getLinearVelocity(bb, new Vector3d()).length() > profile.maxSpeed())
        throw new IllegalArgumentException("message.guidedrailways.coupler.too_fast");
    }
    return geometry;
  }

  static boolean connected(ServerLevel level, CouplerEndpoint e) {
    var data = CouplerSavedData.get(level);
    return data.owns(e.endpointId, e.getBlockPos()) && data.links.forEndpoint(e.endpointId) != null;
  }

  /** Trackside operation reuses exactly the same saved links and physics joints as a wrench. */
  static int trackOperation(
      ServerLevel level,
      ServerSubLevel ba,
      ServerSubLevel bb,
      boolean couple,
      boolean decouple,
      boolean execute) {
    if (ba == null || bb == null || ba == bb) return 0;
    var s = state(level);
    var data = CouplerSavedData.get(level);
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    boolean stopped =
        CouplerGeometry.slowEnough(
            pipe.getLinearVelocity(ba, new Vector3d()),
            pipe.getLinearVelocity(bb, new Vector3d()),
            pipe.getAngularVelocity(ba, new Vector3d()),
            pipe.getAngularVelocity(bb, new Vector3d()));
    if (!stopped) return 0;
    var endsA =
        s.endpoints.values().stream()
            .filter(e -> loaded(level, e) && body(level, e) == ba)
            .sorted(Comparator.comparing(e -> e.endpointId))
            .toList();
    var endsB =
        s.endpoints.values().stream()
            .filter(e -> loaded(level, e) && body(level, e) == bb)
            .sorted(Comparator.comparing(e -> e.endpointId))
            .toList();
    CouplerEndpoint first = null, second = null;
    int mode = 0;
    double best = Double.POSITIVE_INFINITY;
    boolean sameTrain = CargoStorage.consist(level, ba).bodies().containsKey(bb.getUniqueId());
    for (var a : endsA)
      for (var b : endsB) {
        var link = data.links.forEndpoint(a.endpointId);
        boolean linked =
            link != null && (link.a().equals(b.endpointId) || link.b().equals(b.endpointId));
        boolean free =
            linked
                || CouplerKind.compatible(RailwayHardware.kind(a), RailwayHardware.kind(b))
                    && link == null
                    && data.links.forEndpoint(b.endpointId) == null;
        int candidate = RailCouplerMode.choose(linked, sameTrain, stopped, free, couple, decouple);
        if (candidate == 0) continue;
        double distance =
            VehiclePhysics.world(ba, head(a)).distanceSquared(VehiclePhysics.world(bb, head(b)));
        if (!linked)
          try {
            engagement(level, a, b, ba, bb);
          } catch (IllegalArgumentException rejected) {
            continue;
          }
        if (distance < best) {
          best = distance;
          first = a;
          second = b;
          mode = candidate;
        }
      }
    if (mode == 0 || !execute) return mode;
    if (mode == 2) disconnect(level, first.endpointId, "trackside");
    else connect(level, first, second);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[RailwayCoupler] mode={} bodies={}/{} endpoints={}/{}",
            mode,
            ba.getUniqueId(),
            bb.getUniqueId(),
            first.endpointId,
            second.endpointId);
    return mode;
  }

  record BodyLink(ServerSubLevel a, ServerSubLevel b, Vector3d anchorA, Vector3d anchorB) {}

  static List<BodyLink> bodyLinks(ServerLevel level) {
    var s = STATES.get(level);
    if (s == null) return List.of();
    var result = new ArrayList<BodyLink>();
    for (var live : s.live.values())
      if (live.joint.isValid()
          && !live.a.isRemoved()
          && !live.b.isRemoved()
          && loaded(level, live.visualA)
          && loaded(level, live.visualB))
        result.add(
            new BodyLink(
                live.a,
                live.b,
                blockOrigin(live.visualA).add(live.visualA.anchor),
                blockOrigin(live.visualB).add(live.visualB.anchor)));
    return result;
  }

  static boolean blocksRelocation(ServerSubLevel carrier) {
    var level = carrier.getLevel();
    var s = STATES.get(level);
    if (s == null) return false;
    for (var e : s.endpoints.values())
      if (loaded(level, e) && body(level, e) == carrier && connected(level, e)) return true;
    return false;
  }

  static boolean cargoComplete(ServerLevel level, Set<UUID> bodies) {
    var s = STATES.get(level);
    if (s == null) return true;
    for (var e : s.endpoints.values())
      if (loaded(level, e)) {
        var b = body(level, e);
        if (b == null || !bodies.contains(b.getUniqueId())) continue;
        var link = CouplerSavedData.get(level).links.forEndpoint(e.endpointId);
        if (link == null) continue;
        var live = s.live.get(link.id());
        if (live == null
            || !live.joint.isValid()
            || !loaded(level, live.visualA)
            || !loaded(level, live.visualB)
            || live.a.isRemoved()
            || live.b.isRemoved()
            || !bodies.contains(live.a.getUniqueId())
            || !bodies.contains(live.b.getUniqueId())) return false;
      }
    return true;
  }

  static void step(SubLevelPhysicsSystem physics) {
    var level = physics.getLevel();
    var s = STATES.get(level);
    if (s == null) return;
    s.endpoints.entrySet().removeIf(e -> !loaded(level, e.getValue()));
    var data = CouplerSavedData.get(level);
    s.waiting.keySet().removeIf(id -> data.links.get(id) == null);
    for (var id : List.copyOf(s.live.keySet())) if (data.links.get(id) == null) close(s, id);
    for (var link : data.links.all())
      try {
        reconcile(level, link);
      } catch (RuntimeException error) {
        close(s, link.id());
        if (s.warned.add(link.id()))
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
              .error("[Coupler] joint rebuild failed link={}", link.id(), error);
      }
    s.pending.values().removeIf(p -> p.expires < level.getGameTime());
    CouplerTransfers.prune(level);
  }

  private static void reconcile(ServerLevel level, CouplingLinks.Link link) {
    var s = state(level);
    var a = s.endpoints.get(link.a());
    var b = s.endpoints.get(link.b());
    if (!loaded(level, a) || !loaded(level, b)) {
      close(s, link.id());
      waiting(s, link, "endpoint_not_registered_or_unloaded");
      return;
    }
    if (!CouplerKind.compatible(RailwayHardware.kind(a), RailwayHardware.kind(b))) {
      disconnect(level, a.endpointId, "incompatible_hardware");
      return;
    }
    var ba = body(level, a);
    var bb = body(level, b);
    if (ba == null || bb == null) {
      close(s, link.id());
      waiting(s, link, "physical_body_not_ready");
      return;
    }
    if (ba == bb) {
      disconnect(level, link.a(), "merged_into_same_body");
      return;
    }
    var old = s.live.get(link.id());
    if (old != null
        && (!old.joint.isValid()
            || old.a != ba
            || old.b != bb
            || !old.pa.equals(a.getBlockPos())
            || !old.pb.equals(b.getBlockPos()))) {
      close(s, link.id());
      old = null;
    }
    if (!a.hasGeometry || !b.hasGeometry) {
      disconnect(level, link.a(), "missing_saved_geometry");
      return;
    }
    a.visualPeer(b);
    b.visualPeer(a);
    var kind = RailwayHardware.kind(a);
    double progress = Math.min(a.hardwareProgress(0), b.hardwareProgress(0));
    if (!CouplerMechanism.carries(kind, progress)) {
      waiting(s, link, "mechanism_closing");
      if (VehiclePhysics.world(ba, head(a)).distance(VehiclePhysics.world(bb, head(b)))
          > CouplerMechanism.profile(kind).reach() + .1)
        disconnect(level, a.endpointId, "coupling_interrupted");
      return;
    }
    if (old == null) {
      old = CouplerJoint.create(link.id(), ba, bb, a, b);
      s.live.put(link.id(), old);
      s.warned.remove(link.id());
      s.waiting.remove(link.id());
      TrainConsists.invalidate(level);
    }
    old.update(link.id());
  }

  static void beforeMove(ServerLevel from, ServerLevel to, BlockPos oldPos, BlockPos newPos) {
    var endpoint = CouplerEndpoint.get(from.getBlockEntity(oldPos));
    if (endpoint == null) return;
    register(from, endpoint);
    if (state(from).endpoints.get(endpoint.endpointId) != endpoint
        || !CouplerSavedData.get(from).owns(endpoint.endpointId, oldPos)) return;
    if (from != to) disconnect(from, endpoint.endpointId, "dimension_changed");
    CouplerTransfers.capture(from, to, oldPos, newPos, endpoint);
    suspend(from, endpoint.endpointId);
  }

  static void afterMove(ServerLevel to, BlockPos pos) {
    var e = CouplerEndpoint.get(to.getBlockEntity(pos));
    if (e != null) register(to, e);
  }

  static void unload(ServerLevel level) {
    CouplerDiscovery.unload(level);
    var s = STATES.remove(level);
    if (s != null) s.live.values().forEach(live -> live.close(false));
    CouplerTransfers.unload(level);
  }
}
