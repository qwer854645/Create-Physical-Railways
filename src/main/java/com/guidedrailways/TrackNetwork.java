package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Read-only Create graph adapter. It never creates blocks, rail colliders or train entities. */
public final class TrackNetwork {
  public record Path(UUID graph, int a, int b, TrackGraph network, TrackEdge edge) {
    boolean supports(RailGauge gauge) {
      return gauge.accepts(edge.getTrackMaterial().trackType.id.toString());
    }

    Vector3d at(double t) {
      var v = edge.getPosition(network, t);
      return new Vector3d(v.x, v.y, v.z);
    }

    boolean adjacent(Path p) {
      return graph.equals(p.graph) && (a == p.a || a == p.b || b == p.a || b == p.b);
    }
  }

  public record Sample(Path path, double t, Vector3d position, Vector3d tangent, Vector3d up) {
    double lateralCurvature() {
      if (!path.edge.isTurn()) return 0;
      double span = .25 / Math.max(.01, path.edge.getLength()),
          lo = Math.max(0, t - span),
          hi = Math.min(1, t + span);
      var a = PathGeometry.tangent(path::at, lo);
      var b = PathGeometry.tangent(path::at, hi);
      return DerailmentModel.lateralCurvature(a, b, tangent, up, path.at(lo).distance(path.at(hi)));
    }

    Quaterniond orientation() {
      var x = new Vector3d(tangent);
      var y = new Vector3d(up);
      var z = new Vector3d(x).cross(y).normalize();
      y.set(z).cross(x).normalize();
      return new Quaterniond().setFromNormalized(new Matrix3d(x, y, z));
    }
  }

  interface RoutePolicy {
    int choose(Path incoming, int node, int from, int[] exits, int proposed, Vector3d position);

    boolean commit(Path incoming, int node, int from, int[] exits, int selected, Vector3d position);

    boolean blocked();

    void observe(Vector3dc position);

    BranchSelect.Steer steer();

    Sample leader();

    default boolean mayPass(Path incoming, int node, int from, int to) {
      return true;
    }

    default RailGauge gauge() {
      return RailGauge.STANDARD;
    }
  }

  private record PathKey(UUID graph, int a, int b) {}

  static final long AUDIT_TICKS = 200;
  static final int INDEX_WORK = 512;
  static final long INDEX_NANOS = 1_000_000;

  private record GraphIndex(Map<PathKey, Path> paths, Map<Long, Set<Path>> cells, int memberships) {}

  private static final class GraphSlot {
    TrackGraph source;
    GraphIndex active;
    Build build;
    long version, next;
    boolean queued, cleanupQueued;
    Iterator<Long> cleanup;
    final Set<Long> references = new HashSet<>();
  }

  private static final class Cell {
    final Set<GraphSlot> graphs = new HashSet<>();
    long generation = -1;
    List<Path> cached = List.of();
  }

  private static final class Index {
    long generation, tick = Long.MIN_VALUE;
    final Map<UUID, GraphSlot> slots = new LinkedHashMap<>();
    final Map<Long, Cell> cells = new HashMap<>();
    final ArrayDeque<GraphSlot> pending = new ArrayDeque<>();
    final ArrayDeque<GraphSlot> cleanup = new ArrayDeque<>();

    void clean(GraphSlot slot) {
      slot.cleanup = slot.references.iterator();
      if (!slot.cleanupQueued) { cleanup.add(slot); slot.cleanupQueued = true; }
    }

    Path path(PathKey key) {
      var slot = slots.get(key.graph);
      return slot == null || slot.active == null ? null : slot.active.paths.get(key);
    }

    List<Path> candidates(long key) {
      var cell = cells.get(key);
      if (cell == null) return List.of();
      if (cell.generation != generation) {
        var paths = new ArrayList<Path>();
        for (var slot : cell.graphs)
          if (slot.active != null) paths.addAll(slot.active.cells.getOrDefault(key, Set.of()));
        cell.cached = List.copyOf(paths);
        cell.generation = generation;
      }
      return cell.cached;
    }
  }

  /** Staging never changes active query data. Each step visits one node, edge or spatial sample. */
  private static final class Build {
    final TrackGraph graph;
    final long version;
    final Iterator<TrackNodeLocation> nodes;
    final Map<PathKey, Path> paths = new HashMap<>();
    final Map<Long, Set<Path>> cells = new HashMap<>();
    Iterator<Map.Entry<TrackNode, TrackEdge>> edges = Collections.emptyIterator();
    TrackNode node;
    Path spatial;
    int sample, samples, memberships;
    boolean changed;

    Build(TrackGraph graph) {
      this.graph = graph;
      version = TrackTopology.revision(graph);
      nodes = graph.getNodes().iterator();
    }

    boolean step(ServerLevel level, Index index, GraphSlot slot) {
      if (spatial != null) {
        var p = spatial.at((double) sample / samples);
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) {
            long key = cell(p.x + dx * 16, p.z + dz * 16);
            if (cells.computeIfAbsent(key, k -> new HashSet<>()).add(spatial)) {
              memberships++;
              if (slot.active == null || !slot.active.cells.getOrDefault(key, Set.of()).contains(spatial)) changed = true;
            }
            if (slot.references.add(key)) {
              var bucket = index.cells.computeIfAbsent(key, k -> new Cell());
              bucket.graphs.add(slot);
              bucket.generation = -1;
            }
          }
        if (++sample > samples) spatial = null;
        return false;
      }
      if (edges.hasNext()) {
        var edge = edges.next();
        var other = edge.getKey();
        if (node.getNetId() < other.getNetId() && !edge.getValue().isInterDimensional()) {
          var key = key(graph.id, node.getNetId(), other.getNetId());
          var old = slot.active == null ? null : slot.active.paths.get(key);
          spatial = old != null && old.network == graph && old.edge == edge.getValue()
              ? old : new Path(graph.id, key.a, key.b, graph, edge.getValue());
          if (spatial != old) changed = true;
          paths.put(key, spatial);
          sample = 0;
          samples = Math.max(1, (int) Math.ceil(spatial.edge.getLength() / 4));
        }
        return false;
      }
      if (nodes.hasNext()) {
        var location = nodes.next();
        node = location.dimension.equals(level.dimension()) ? graph.locateNode(location) : null;
        edges = connections(graph, node).entrySet().iterator();
        return false;
      }
      return true;
    }
  }

  private static final Map<ServerLevel, Index> INDEX = new WeakHashMap<>();

  private static long cell(double x, double z) {
    return net.minecraft.world.level.ChunkPos.asLong(
        (int) Math.floor(x / 16), (int) Math.floor(z / 16));
  }

  private static PathKey key(Path p) {
    return new PathKey(p.graph, p.a, p.b);
  }

  private static PathKey key(UUID graph, int a, int b) {
    return a <= b ? new PathKey(graph, a, b) : new PathKey(graph, b, a);
  }

  public static void refresh(ServerLevel level) {
    refresh(level, false);
  }

  private static void refresh(ServerLevel level, boolean force) {
    var idx = INDEX.computeIfAbsent(level, l -> new Index());
    long now = level.getGameTime();
    if (!force && idx.tick == now) return;
    idx.tick = now;
    boolean synchronous = force || idx.slots.isEmpty() || Boolean.getBoolean("guidedrailways.fullTrackIndex");
    var graphs = Create.RAILWAYS.sided(level).trackNetworks;
    long start = TrainConfig.PERFORMANCE_LOGGING.get() ? System.nanoTime() : 0;
    boolean published = false;
    for (var entry : List.copyOf(idx.slots.entrySet()))
      if (!graphs.containsKey(entry.getKey())) {
        var slot = idx.slots.remove(entry.getKey());
        slot.active = null;
        slot.build = null;
        idx.pending.remove(slot);
        slot.queued = false;
        idx.clean(slot);
        idx.generation++;
        published = true;
      }
    for (var graph : graphs.values()) {
      var slot = idx.slots.computeIfAbsent(graph.id, k -> new GraphSlot());
      long version = TrackTopology.revision(graph);
      boolean dirty = force || slot.active == null || slot.version != version || now < slot.next - AUDIT_TICKS - 20
          || now >= slot.next || slot.source != graph;
      if (dirty && (slot.build == null || slot.build.graph != graph || slot.build.version != version || force)) {
        slot.build = new Build(graph);
        if (!slot.queued) { idx.pending.add(slot); slot.queued = true; }
      }
    }
    long deadline = synchronous ? Long.MAX_VALUE : System.nanoTime() + INDEX_NANOS;
    int work = synchronous ? Integer.MAX_VALUE : INDEX_WORK;
    while (!idx.pending.isEmpty() && work-- > 0 && System.nanoTime() < deadline) {
      var slot = idx.pending.removeFirst();
      slot.queued = false;
      var build = slot.build;
      try {
        if (build.step(level, idx, slot)) {
          published |= publish(idx, slot, now);
        } else { idx.pending.addLast(slot); slot.queued = true; }
      } catch (ConcurrentModificationException changed) {
        // Uninstrumented third-party edits may invalidate iterators between ticks. Retry a clean
        // staging build; live() continues rejecting deleted/replaced edges in the active index.
        slot.build = null;
        slot.next = now;
      }
    }
    int cleanup = synchronous ? Integer.MAX_VALUE : INDEX_WORK;
    while (!idx.cleanup.isEmpty() && cleanup-- > 0) {
      var slot = idx.cleanup.removeFirst();
      if (slot.build != null) { idx.cleanup.addLast(slot); continue; }
      try {
        if (!slot.cleanup.hasNext()) { slot.cleanupQueued = false; continue; }
        long key = slot.cleanup.next();
        var bucket = idx.cells.get(key);
        if (bucket != null) { bucket.cached = List.of(); bucket.generation = -1; }
        if (slot.active == null || !slot.active.cells.containsKey(key)) {
          if (bucket != null) {
            bucket.graphs.remove(slot);
            if (bucket.graphs.isEmpty()) idx.cells.remove(key);
          }
          slot.cleanup.remove();
        }
      } catch (ConcurrentModificationException changed) { slot.cleanup = slot.references.iterator(); }
      idx.cleanup.addLast(slot);
    }
    if (start != 0) PerfCounters.index(level, System.nanoTime() - start, published);
  }

  private static boolean publish(Index index, GraphSlot slot, long now) {
    var build = slot.build;
    slot.version = build.version;
    slot.source = build.graph;
    slot.next = now + AUDIT_TICKS + Math.floorMod(build.graph.id.hashCode(), 20);
    boolean changed = slot.active == null || build.changed || slot.active.paths.size() != build.paths.size()
        || slot.active.memberships != build.memberships;
    if (changed) {
      slot.active = new GraphIndex(Collections.unmodifiableMap(build.paths),
          Collections.unmodifiableMap(build.cells), build.memberships);
      index.generation++;
    }
    slot.build = null;
    index.clean(slot);
    return changed;
  }

  /** Preserve immediate navigation/guide semantics after a known edit. Complete only this graph;
   * an ordinary periodic audit of an unchanged graph remains in the background. */
  private static void ensureReady(ServerLevel level, UUID id) {
    var graph = Create.RAILWAYS.sided(level).trackNetworks.get(id);
    if (graph == null) return;
    var index = INDEX.computeIfAbsent(level, l -> new Index());
    var slot = index.slots.computeIfAbsent(id, k -> new GraphSlot());
    long version = TrackTopology.revision(graph);
    if (slot.active != null && slot.source == graph && slot.version == version) return;
    if (slot.build == null || slot.build.graph != graph || slot.build.version != version) slot.build = new Build(graph);
    index.pending.remove(slot);
    slot.queued = false;
    long start = TrainConfig.PERFORMANCE_LOGGING.get() ? System.nanoTime() : 0;
    try {
      while (!slot.build.step(level, index, slot)) {}
      boolean changed = publish(index, slot, level.getGameTime());
      if (start != 0) PerfCounters.index(level, System.nanoTime() - start, changed);
    } catch (ConcurrentModificationException changed) {
      slot.build = null;
      slot.next = level.getGameTime();
    }
  }

  /** Create may remove a node or its adjacency map before our throttled index is rebuilt. */
  static Map<TrackNode, TrackEdge> connections(TrackGraph graph, TrackNode node) {
    if (node == null) return Collections.emptyMap();
    var edges = graph.getConnectionsFrom(node);
    return edges == null ? Collections.emptyMap() : edges;
  }

  static boolean live(ServerLevel level, Path path) {
    var index = INDEX.get(level);
    return path != null
        && index != null
        && index.path(key(path)) == path
        && Create.RAILWAYS.sided(level).trackNetworks.get(path.graph) == path.network
        && connections(path.network, path.network.getNode(path.a)).get(path.network.getNode(path.b))
            == path.edge;
  }

  static long generation(ServerLevel level) {
    var index = INDEX.get(level);
    return index == null ? 0 : index.generation;
  }

  static Collection<Path> paths(ServerLevel level, UUID graph) {
    ensureReady(level, graph);
    var index = INDEX.get(level);
    var slot = index == null ? null : index.slots.get(graph);
    return slot == null || slot.active == null
        ? List.of()
        : slot.active.paths.values().stream()
            .filter(p -> live(level, p))
            .toList();
  }

  private static Sample project(Path path, Vector3dc query, Vector3dc heading, Double hint) {
    double seed = 0, span;
    if (!path.edge.isTurn()) {
      var a = path.at(0);
      var delta = path.at(1).sub(a);
      double lengthSq = delta.lengthSquared();
      seed =
          lengthSq > 1e-10 ? Math.clamp(new Vector3d(query).sub(a).dot(delta) / lengthSq, 0, 1) : 0;
      span = 0;
    } else if (hint != null) {
      seed = hint;
      span = Math.clamp(.75 / Math.max(.01, path.edge.getLength()), .005, .5);
    } else {
      double best = Double.POSITIVE_INFINITY;
      int n = Math.clamp((int) Math.ceil(path.edge.getLength() / 2), 8, 512);
      for (int i = 0; i <= n; i++) {
        double t = (double) i / n;
        double d = path.at(t).distanceSquared(query);
        if (d < best) {
          best = d;
          seed = t;
        }
      }
      span = 1.0 / n;
    }
    double lo = Math.max(0, seed - span), hi = Math.min(1, seed + span);
    for (int i = 0; i < 18 && hi > lo; i++) {
      double a = (2 * lo + hi) / 3, b = (lo + 2 * hi) / 3;
      if (path.at(a).distanceSquared(query) < path.at(b).distanceSquared(query)) hi = b;
      else lo = a;
    }
    double t = (lo + hi) * .5;
    var p = path.at(t);
    if (path.at(0).distanceSquared(query) < p.distanceSquared(query)) {
      t = 0;
      p = path.at(0);
    }
    if (path.at(1).distanceSquared(query) < p.distanceSquared(query)) {
      t = 1;
      p = path.at(1);
    }
    var original = PathGeometry.tangent(path::at, t);
    var x = new Vector3d(original);
    if (x.dot(heading) < 0) x.negate();
    var normal = path.edge.getNormal(path.network, t);
    var up = new Vector3d(normal.x, normal.y, normal.z).normalize();
    // Reject longitudinal overrun at a finite endpoint; no invisible guide beyond a broken rail.
    if (PathGeometry.beyondEndpoint(t, p, query, original, .08)) return null;
    return new Sample(path, t, p, x, up);
  }

  /**
   * Navigation observes the physics-owned edge; proximity must never select a neighbouring branch.
   */
  static Sample resample(
      ServerLevel level, Sample current, Vector3dc point, Vector3dc heading, double radius) {
    if (current == null || !live(level, current.path)) return null;
    var sample = project(current.path, point, heading, current.t);
    if (sample == null) sample = current; // Physics may not have published the node crossing yet.
    return sample.position.distanceSquared(point) <= radius * radius
            && level.hasChunkAt(
                BlockPos.containing(sample.position.x, sample.position.y, sample.position.z))
        ? sample
        : null;
  }

  static Sample follow(
      ServerLevel level,
      Vector3dc point,
      Vector3dc heading,
      Sample previous,
      Sample leader,
      double radius,
      boolean reverse,
      RoutePolicy policy) {
    if (policy != null) policy.observe(point);
    PerfCounters.query(level);
    var idx = INDEX.get(level);
    if (previous != null && idx != null) {
      var live = idx.path(key(previous.path));
      if (live == null || !live(level, live)) {
        ensureReady(level, previous.path.graph);
        live = idx.path(key(previous.path));
      }
      if (live != null
          && live(level, live)
          && live.supports(policy == null ? RailGauge.STANDARD : policy.gauge())) {
        var sample = project(live, point, heading, previous.t);
        if (sample != null
            && sample.t > .0001
            && sample.t < .9999
            && sample.position.distanceSquared(point) < .09
            && sample.tangent.dot(heading) > .75
            && level.hasChunkAt(
                BlockPos.containing(sample.position.x, sample.position.y, sample.position.z)))
          return sample;
        var branched =
            advance(level, idx, live, point, heading, previous, leader, radius, reverse, policy);
        if (branched != null) return branched;
        if (policy != null
            && policy.blocked()
            && previous.position.distanceSquared(point) <= radius * radius
            && level.hasChunkAt(
                BlockPos.containing(previous.position.x, previous.position.y, previous.position.z)))
          return previous;
        // A locked passage must never be bypassed by nearest-edge recovery.
        if (policy != null)
          return sample != null
                  && sample.position.distanceSquared(point) <= radius * radius
                  && level.hasChunkAt(
                      BlockPos.containing(sample.position.x, sample.position.y, sample.position.z))
              ? sample
              : null;
      }
      if (policy != null) return null;
    }
    return nearest(
        level,
        point,
        heading,
        previous == null ? null : previous.path,
        radius,
        policy == null ? RailGauge.STANDARD : policy.gauge());
  }

  /** Create-style node transition: enumerate outgoing edges, then steer / follow. */
  private static Sample advance(
      ServerLevel level,
      Index idx,
      Path live,
      Vector3dc point,
      Vector3dc heading,
      Sample previous,
      Sample leader,
      double radius,
      boolean reverse,
      RoutePolicy policy) {
    ensureReady(level, live.graph);
    var increasing = PathGeometry.tangent(live::at, previous.t);
    boolean alongIncreasing = BranchSelect.exitsIncreasing(increasing, heading, reverse);
    double endT = alongIncreasing ? 1 : 0;
    if (!SeamRules.canAdvance(previous.t, endT, previous.position.distanceSquared(live.at(endT))))
      return null;
    int exitId = alongIncreasing ? live.b : live.a, enterId = alongIncreasing ? live.a : live.b;
    var exit = live.network.getNode(exitId);
    var enter = live.network.getNode(enterId);
    if (exit == null || enter == null) return null;
    var current = connections(live.network, enter).get(exit);
    if (current == null) return null;
    var options = new ArrayList<Branch>();
    var exitLinks = connections(live.network, exit);
    collect(options, idx, level, live, exit, enter, current, policy, true);
    if (options.isEmpty() && SeamRules.pureSeam(exitLinks.size())) {
      // Only one candidate exists at a two-way seam, so geometric compatibility cannot be choosing
      // between branches here; refusing it would leave the axle without an edge. Gauge, loaded
      // chunks and the projection radius are still checked before anything is accepted.
      collect(options, idx, level, live, exit, enter, current, policy, false);
      if (!options.isEmpty())
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .info(
                "[SeamFallback] graph={} node={} entered={} used the only other connection",
                live.graph,
                exit.getNetId(),
                enter.getNetId());
    }
    if (options.isEmpty()) return null;
    options.sort(Comparator.comparingInt(c -> c.node.getNetId()));
    var incoming = PathGeometry.tangent(live::at, endT).mul(alongIncreasing ? 1 : -1);
    var directions = new Vector3dc[options.size()];
    for (int i = 0; i < options.size(); i++) {
      var p = options.get(i).path;
      directions[i] =
          PathGeometry.tangent(p::at, p.a == exitId ? 0 : 1).mul(p.a == exitId ? 1 : -1);
    }
    int pick =
        BranchSelect.choose(
            incoming,
            previous.up,
            directions,
            policy == null ? BranchSelect.Steer.NONE : policy.steer());
    if (!RailwaysCompat.navigable(options.get(pick).edge)) {
      var available = new ArrayList<Integer>();
      for (int i = 0; i < options.size(); i++)
        if (RailwaysCompat.navigable(options.get(i).edge)) available.add(i);
      if (!available.isEmpty()) {
        var open = new Vector3dc[available.size()];
        for (int i = 0; i < open.length; i++) open[i] = directions[available.get(i)];
        pick =
            available.get(
                BranchSelect.choose(
                    incoming,
                    previous.up,
                    open,
                    policy == null ? BranchSelect.Steer.NONE : policy.steer()));
      }
    }
    Branch chosen = options.get(pick);
    if (policy != null && connections(live.network, exit).size() > 2) {
      var target = policy.leader();
      if (target != null && !key(target.path).equals(key(live))) {
        var followed = followLeader(options, target);
        if (followed != null) chosen = followed;
      }
      int[] ids = options.stream().mapToInt(c -> c.node.getNetId()).toArray();
      int selected =
          policy.choose(live, exitId, enterId, ids, chosen.node.getNetId(), live.at(endT));
      chosen = null;
      for (var c : options)
        if (c.node.getNetId() == selected) {
          chosen = c;
          break;
        }
      if (chosen == null) return null;
    } else if (policy == null && leader != null) {
      var followed = followLeader(options, leader);
      if (followed != null) chosen = followed;
    }
    if (policy != null && !policy.mayPass(live, exitId, enterId, chosen.node.getNetId()))
      return null;
    if (policy == null && !RailwaysCompat.navigable(chosen.edge)) return null;
    double hint = chosen.path.a == exit.getNetId() ? 0 : 1;
    var sample = project(chosen.path, point, heading, hint);
    if (sample == null || sample.position.distanceSquared(point) > radius * radius) return null;
    if (!level.hasChunkAt(
        BlockPos.containing(sample.position.x, sample.position.y, sample.position.z))) return null;
    if (policy != null && connections(live.network, exit).size() > 2) {
      int[] ids = options.stream().mapToInt(c -> c.node.getNetId()).toArray();
      if (!policy.commit(live, exitId, enterId, ids, chosen.node.getNetId(), live.at(endT)))
        return null;
    }
    return sample;
  }

  private record Branch(TrackNode node, TrackEdge edge, Path path) {}

  /**
   * Candidates at the exit node: everything but the edge that was entered from, inter-dimensional
   * edges, edges the gauge cannot use. {@code requireGeometry} is false only for the two-way seam
   * fallback, where compatibility cannot be choosing between branches.
   */
  private static void collect(
      List<Branch> options,
      Index idx,
      ServerLevel level,
      Path live,
      TrackNode exit,
      TrackNode enter,
      TrackEdge current,
      RoutePolicy policy,
      boolean requireGeometry) {
    for (var entry : connections(live.network, exit).entrySet()) {
      if (entry.getKey() == enter || entry.getValue().isInterDimensional()) continue;
      if (requireGeometry && !RailwaysCompat.geometry(current, entry.getValue())) continue;
      var path = idx.path(key(live.graph, exit.getNetId(), entry.getKey().getNetId()));
      if (path == null
          || !live(level, path)
          || !path.supports(policy == null ? RailGauge.STANDARD : policy.gauge())) continue;
      options.add(new Branch(entry.getKey(), entry.getValue(), path));
    }
  }

  /** Read ahead without committing route memory or moving a body. */
  static double switchDistance(
      ServerLevel level,
      Sample sample,
      Vector3dc heading,
      boolean reverse,
      RoutePolicy policy,
      double horizon,
      double clearance) {
    if (sample != null) ensureReady(level, sample.path.graph);
    if (RailwaysCompat.bridge == null || sample == null || !live(level, sample.path))
      return Double.POSITIVE_INFINITY;
    Path path = sample.path;
    boolean inc =
        BranchSelect.exitsIncreasing(PathGeometry.tangent(path::at, sample.t), heading, reverse);
    double offset = sample.t * path.edge.getLength(),
        distance = (inc ? 1 - sample.t : sample.t) * path.edge.getLength();
    var idx = INDEX.get(level);
    for (int depth = 0; depth < 128; depth++) {
      if (!live(level, path)) return Double.POSITIVE_INFINITY;
      double start = distance - (inc ? path.edge.getLength() - offset : offset),
          bufferDistance = Double.POSITIVE_INFINITY;
      for (double buffer : RailwaysCompat.buffers(path)) {
        double along = inc ? buffer - offset : offset - buffer;
        if (along >= -.05)
          bufferDistance = Math.min(bufferDistance, Math.max(0, start + along - clearance));
      }
      if (Double.isFinite(bufferDistance)) return bufferDistance;
      if (distance > horizon) return Double.POSITIVE_INFINITY;
      int node = inc ? path.b : path.a, from = inc ? path.a : path.b;
      var exit = path.network.getNode(node);
      var enter = path.network.getNode(from);
      var incoming = connections(path.network, enter).get(exit);
      if (incoming == null) return Double.POSITIVE_INFINITY;
      var options = new ArrayList<Branch>();
      for (var entry : connections(path.network, exit).entrySet()) {
        if (entry.getKey() == enter
            || entry.getValue().isInterDimensional()
            || !RailwaysCompat.geometry(incoming, entry.getValue())) continue;
        var next = idx.path(key(path.graph, node, entry.getKey().getNetId()));
        if (next != null && live(level, next) && next.supports(policy.gauge()))
          options.add(new Branch(entry.getKey(), entry.getValue(), next));
      }
      if (options.isEmpty()) return Double.POSITIVE_INFINITY;
      options.sort(Comparator.comparingInt(b -> b.node.getNetId()));
      if (options.size() > 1 || connections(path.network, exit).size() > 2) {
        var available = options.stream().filter(b -> RailwaysCompat.navigable(b.edge)).toList();
        int proposed = -1;
        if (!available.isEmpty()) {
          var directions = new Vector3dc[available.size()];
          for (int i = 0; i < directions.length; i++) {
            var b = available.get(i);
            directions[i] =
                PathGeometry.tangent(b.path::at, b.path.a == node ? 0 : 1)
                    .mul(b.path.a == node ? 1 : -1);
          }
          var tangent = PathGeometry.tangent(path::at, inc ? 1 : 0).mul(inc ? 1 : -1);
          proposed =
              available
                  .get(BranchSelect.choose(tangent, sample.up, directions, policy.steer()))
                  .node
                  .getNetId();
        }
        int selected =
            policy.choose(
                path,
                node,
                from,
                options.stream().mapToInt(b -> b.node.getNetId()).toArray(),
                proposed,
                path.at(inc ? 1 : 0));
        if (selected < 0 || !policy.mayPass(path, node, from, selected))
          return Math.max(0, distance - .4);
        Branch chosen = null;
        for (var option : options)
          if (option.node.getNetId() == selected) {
            chosen = option;
            break;
          }
        if (chosen == null) return Math.max(0, distance - .4);
        options.clear();
        options.add(chosen);
      }
      var next = options.getFirst();
      inc = next.path.a == node;
      path = next.path;
      offset = inc ? 0 : path.edge.getLength();
      distance += path.edge.getLength();
    }
    return Double.POSITIVE_INFINITY;
  }

  /** Prefer the outgoing edge that reaches the leader's current nodes (Create {@code follow}). */
  private static Branch followLeader(List<Branch> options, Sample leader) {
    int la = leader.path.a, lb = leader.path.b;
    for (var c : options)
      if (c.path.equals(leader.path) || c.node.getNetId() == la || c.node.getNetId() == lb)
        return c;
    record Step(TrackNode node, TrackEdge via) {}
    var frontiers = new ArrayList<ArrayDeque<Step>>();
    var visited = new ArrayList<HashSet<TrackEdge>>();
    for (var c : options) {
      var q = new ArrayDeque<Step>();
      q.add(new Step(c.node, c.edge));
      frontiers.add(q);
      var seen = new HashSet<TrackEdge>();
      seen.add(c.edge);
      visited.add(seen);
    }
    for (int depth = 0; depth < 256; depth++)
      for (int j = 0; j < options.size(); j++) {
        var frontier = frontiers.get(j);
        if (frontier.isEmpty()) continue;
        var step = frontier.removeFirst();
        if (step.node.getNetId() == la || step.node.getNetId() == lb) return options.get(j);
        for (var next : connections(options.get(j).path.network, step.node).entrySet()) {
          if (!visited.get(j).add(next.getValue()) || !step.via.canTravelTo(next.getValue()))
            continue;
          frontier.add(new Step(next.getKey(), next.getValue()));
        }
      }
    return null;
  }

  static Sample nearest(
      ServerLevel level,
      Vector3dc point,
      Vector3dc heading,
      Path previous,
      double radius,
      RailGauge gauge) {
    var idx = INDEX.get(level);
    if (idx == null) return null;
    var candidates = idx.candidates(cell(point.x(), point.z()));
    Sample best = null;
    double score = Double.POSITIVE_INFINITY;
    Path live = previous == null ? null : idx.path(key(previous));
    for (var p : candidates) {
      if (!live(level, p) || !p.supports(gauge)) continue;
      if (live != null && !p.equals(live) && !p.adjacent(live)) continue;
      var sample = project(p, point, heading, null);
      if (sample == null) continue;
      double d = sample.position.distanceSquared(point);
      double align = sample.tangent.dot(heading);
      if (d > radius * radius
          || align < .5
          || !level.hasChunkAt(
              BlockPos.containing(sample.position.x, sample.position.y, sample.position.z)))
        continue;
      double rank = d + .02 * (1 - align);
      if (live != null && key(p).equals(key(live))) rank -= .00002;
      if (rank < score) {
        score = rank;
        best = sample;
      }
    }
    return best;
  }

  /** Interactive actions must not use the half-second background index throttle. */
  public static void refreshNow(ServerLevel level) {
    refresh(level, true);
  }

  static Sample pick(net.minecraft.server.level.ServerPlayer player, RailGauge gauge) {
    var level = player.serverLevel();
    refreshNow(level);
    var eye = player.getEyePosition();
    var look = player.getLookAngle();
    double reach = player.blockInteractionRange();
    var end = eye.add(look.scale(reach));
    var block =
        level.clip(
            new net.minecraft.world.level.ClipContext(
                eye,
                end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player));
    if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
      end = block.getLocation().add(look.scale(.05));
    var a = new Vector3d(eye.x, eye.y, eye.z);
    var b = new Vector3d(end.x, end.y, end.z);
    var heading = new Vector3d(look.x, look.y, look.z);
    var idx = INDEX.get(level);
    var candidates = new HashSet<Path>();
    for (int i = 0; i <= 16; i++) {
      var q = new Vector3d(a).lerp(b, i / 16.0);
      candidates.addAll(idx.candidates(cell(q.x, q.z)));
    }
    Sample best = null;
    double score = Double.POSITIVE_INFINITY;
    for (var path : candidates) {
      var pathGauge =
          gauge == null
              ? RailGauge.forTrack(path.edge.getTrackMaterial().trackType.id.toString())
              : gauge;
      if (pathGauge == null || !live(level, path) || !path.supports(pathGauge)) continue;
      int n = Math.clamp((int) Math.ceil(path.edge.getLength() / .25), 1, 16384);
      var prev = at(path, 0, heading);
      for (int i = 1; i <= n; i++) {
        var next = at(path, (double) i / n, heading);
        for (double offset :
            new double[] {0, -.875 * pathGauge.widthScale(), .875 * pathGauge.widthScale()}) {
          double railTop = pathGauge == RailGauge.MONORAIL ? 1 : BogieGeometry.RAIL_TOP;
          var prevTop =
              new Vector3d(prev.position)
                  .fma(railTop, prev.up)
                  .fma(offset, new Vector3d(prev.tangent).cross(prev.up));
          var top =
              new Vector3d(next.position)
                  .fma(railTop, next.up)
                  .fma(offset, new Vector3d(next.tangent).cross(next.up));
          var hit = RailPlacement.raySegment(a, b, prevTop, top);
          double tolerance = offset == 0 ? .55 : .30;
          double rank = hit.distanceSquared() + hit.rayFraction() * 1e-5;
          if (hit.distanceSquared() <= tolerance * tolerance && rank < score) {
            var selectedTop = new Vector3d(prevTop).lerp(top, hit.segmentFraction());
            double depth = new Vector3d(selectedTop).sub(a).dot(heading);
            if (depth < 0 || depth > a.distance(b) + .02) continue;
            double t = (i - 1 + hit.segmentFraction()) / n;
            var chosen = at(path, t, heading);
            if (level.hasChunkAt(
                BlockPos.containing(chosen.position.x, chosen.position.y, chosen.position.z))) {
              best = chosen;
              score = rank;
            }
          }
        }
        prev = next;
      }
    }
    return best;
  }

  private static Sample at(Path path, double t, Vector3dc heading) {
    var tangent = PathGeometry.tangent(path::at, t);
    if (tangent.dot(heading) < 0) tangent.negate();
    var normal = path.edge.getNormal(path.network, t);
    return new Sample(
        path, t, path.at(t), tangent, new Vector3d(normal.x, normal.y, normal.z).normalize());
  }

  private static final class ArcTable {
    final double[] distance;

    ArcTable(Path p) {
      int n = Math.clamp((int) Math.ceil(p.edge.getLength() / .2), 1, 16384);
      distance = new double[n + 1];
      var prev = p.at(0);
      for (int i = 1; i <= n; i++) {
        var next = p.at((double) i / n);
        distance[i] = distance[i - 1] + prev.distance(next);
        prev = next;
      }
    }

    double length() {
      return distance[distance.length - 1];
    }

    double distance(double t) {
      double index = Math.clamp(t, 0, 1) * (distance.length - 1);
      int i = Math.min(distance.length - 2, (int) index);
      return distance[i] + (distance[i + 1] - distance[i]) * (index - i);
    }

    double parameter(double d) {
      d = Math.clamp(d, 0, length());
      int lo = 0, hi = distance.length - 1;
      while (hi - lo > 1) {
        int mid = (lo + hi) / 2;
        if (distance[mid] < d) lo = mid;
        else hi = mid;
      }
      double span = distance[hi] - distance[lo];
      return (lo + (span > 1e-12 ? (d - distance[lo]) / span : 0)) / (distance.length - 1);
    }
  }

  /** One connected, deterministic route, sampled by arc length for placing and fitting bodies. */
  public static final class PlacementRoute implements RailPlacement.Route {
    private record Span(Path path, boolean increasing, double start, ArcTable arc) {
      double end() {
        return start + arc.length();
      }
    }

    final ServerLevel level;
    final Set<RailGauge> gauges;
    final List<Span> spans = new ArrayList<>();
    final Map<Path, ArcTable> tables = new HashMap<>();

    PlacementRoute(ServerLevel level, Sample anchor, double extent, Set<RailGauge> gauges) {
      this.level = level;
      this.gauges = Set.copyOf(gauges);
      if (gauges.stream().anyMatch(g -> !anchor.path.supports(g)))
        throw new IllegalArgumentException("message.guidedrailways.rail.gauge");
      var arc = table(anchor.path);
      boolean increasing = PathGeometry.tangent(anchor.path::at, anchor.t).dot(anchor.tangent) >= 0;
      spans.add(
          new Span(
              anchor.path,
              increasing,
              -(increasing ? arc.distance(anchor.t) : arc.length() - arc.distance(anchor.t)),
              arc));
      for (int i = 0; i < 256 && spans.get(spans.size() - 1).end() < extent; i++)
        if (!extend(true)) break;
      for (int i = 0; i < 256 && spans.get(0).start > -extent; i++) if (!extend(false)) break;
    }

    private ArcTable table(Path p) {
      return tables.computeIfAbsent(p, ArcTable::new);
    }

    private boolean extend(boolean positive) {
      var span = positive ? spans.get(spans.size() - 1) : spans.get(0);
      boolean increasing = positive ? span.increasing : !span.increasing;
      int exitId = increasing ? span.path.b : span.path.a,
          entryId = increasing ? span.path.a : span.path.b;
      var exit = span.path.network.getNode(exitId);
      var entry = span.path.network.getNode(entryId);
      if (exit == null || entry == null) return false;
      var incoming = connections(span.path.network, entry).get(exit);
      if (incoming == null) return false;
      var wanted = PathGeometry.tangent(span.path::at, increasing ? 1 : 0).mul(increasing ? 1 : -1);
      Path best = null;
      double alignment = -2;
      int bestId = Integer.MAX_VALUE;
      var idx = INDEX.get(level);
      for (var e : connections(span.path.network, exit).entrySet()) {
        if (e.getKey() == entry
            || e.getValue().isInterDimensional()
            || !incoming.canTravelTo(e.getValue())) continue;
        var p = idx.path(key(span.path.graph, exitId, e.getKey().getNetId()));
        if (p == null || gauges.stream().anyMatch(g -> !p.supports(g))) continue;
        var dir = PathGeometry.tangent(p::at, p.a == exitId ? 0 : 1).mul(p.a == exitId ? 1 : -1);
        double dot = dir.dot(wanted);
        if (dot > alignment + 1e-9
            || (Math.abs(dot - alignment) < 1e-9 && e.getKey().getNetId() < bestId)) {
          best = p;
          alignment = dot;
          bestId = e.getKey().getNetId();
        }
      }
      if (best == null || alignment < .25) return false;
      var arc = table(best);
      if (arc.length() < 1e-6) return false;
      boolean nextIncreasing = positive ? best.a == exitId : best.b == exitId;
      var next =
          new Span(best, nextIncreasing, positive ? span.end() : span.start - arc.length(), arc);
      if (positive) spans.add(next);
      else spans.add(0, next);
      return true;
    }

    public Sample sample(double d) {
      for (var span : spans)
        if (d >= span.start - 1e-6 && d <= span.end() + 1e-6) {
          double local = Math.clamp(d - span.start, 0, span.arc.length());
          double t = span.arc.parameter(span.increasing ? local : span.arc.length() - local);
          var forward = PathGeometry.tangent(span.path::at, t).mul(span.increasing ? 1 : -1);
          var result = TrackNetwork.at(span.path, t, forward);
          return level.hasChunkAt(
                  BlockPos.containing(result.position.x, result.position.y, result.position.z))
              ? result
              : null;
        }
      return null;
    }

    @Override
    public RailPlacement.Point at(double d) {
      var s = sample(d);
      return s == null ? null : new RailPlacement.Point(s.position, s.tangent, s.up);
    }
  }

  static PlacementRoute placementRoute(
      ServerLevel level, Sample anchor, double extent, Set<RailGauge> gauges) {
    return new PlacementRoute(level, anchor, extent, gauges);
  }

  static void unload(ServerLevel level) {
    INDEX.remove(level);
  }
}
