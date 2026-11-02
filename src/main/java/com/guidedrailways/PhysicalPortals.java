package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.BlockUtil;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

/** Discovery is throttled; known contact only checks one cached portal and member bounds. */
final class PhysicalPortals {
  static final int COOLDOWN_TICKS = 300, MAX_ENTRIES = 256, EXPIRY_TICKS = 600;
  static final double MAX_SPEED = 16;
  private static final int VISITS_PER_TICK = 8, SCAN_TICKS = 5, IDLE_SCAN_TICKS = 20, MAX_PORTAL_POINTS = 1024;
  private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
  private static final class Entry {
    AABB box;
    Vec3 ownerCenter;
    long tick, probed, nextProbe, errorUntil;
    boolean needsExit;
    Session session;
    Entry(AABB box, long now) { this.box = box; ownerCenter = box.getCenter(); tick = probed = now; }
  }
  private record Gate(BlockPos anchor, AABB area, boolean end) {}
  private record Sensor(AABB box, Set<UUID> members) {}
  private static final class Session {
    final UUID root;
    final Set<UUID> members;
    final Gate gate;
    final PortalContact contact = new PortalContact();
    long checked = Long.MIN_VALUE;
    boolean parked, attempting;
    Session(UUID root, Set<UUID> members, Gate gate) { this.root = root; this.members = members; this.gate = gate; }
  }
  private static final class State {
    int cursor;
    final Map<UUID, Entry> entries = new HashMap<>();
  }
  private PhysicalPortals() {}

  static void unload(ServerLevel level) { STATES.remove(level); }

  static void topologyChanged(ServerLevel level, UUID... ids) {
    var state = STATES.get(level); if (state == null) return;
    for (var id : ids) {
      var entry = state.entries.get(id);
      if (entry != null && entry.session != null && !entry.session.attempting)
        release(state, entry.session, level.getServer().overworld().getGameTime());
    }
  }

  static void tick(ServerLevel level) {
    if (LagProbe.skipPhysicalPortals()) return;
    long now = level.getServer().overworld().getGameTime();
    var previous = STATES.get(level);
    if (previous != null) previous.entries.values().removeIf(entry -> now < entry.tick || now - entry.tick > EXPIRY_TICKS);
    boolean nether = TrainConfig.NETHER_PORTAL_TRANSFERS.get() && (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER);
    boolean end = TrainConfig.END_PORTAL_TRANSFERS.get() && (level.dimension() == Level.OVERWORLD || level.dimension() == Level.END);
    if (!nether && !end) { STATES.remove(level); return; }
    var container = SubLevelContainer.getContainer(level);
    if (container == null || SubLevelPhysicsSystem.get(level) == null || SubLevelPhysicsSystem.currentlySteppingSystem != null) return;
    var bodies = List.copyOf(container.getAllSubLevels());
    if (bodies.isEmpty()) { STATES.remove(level); return; }
    var state = STATES.computeIfAbsent(level, ignored -> new State());
    for (int visit = 0; visit < Math.min(VISITS_PER_TICK, bodies.size()); visit++) {
      var body = bodies.get(Math.floorMod(state.cursor++, bodies.size()));
      if (body.isRemoved()) continue;
      // Bound lower bogeys are observed through their carriage, never migrated independently.
      var data = body.getUserDataTag();
      if (BogieMountBinding.owner(data) != null) continue;
      var box = body.boundingBox().toMojang();
      double volume = box.getXsize() * box.getYsize() * box.getZsize();
      if (!Double.isFinite(volume) || volume <= 0 || volume > PhysicalTransfer.MAX_VOLUME) continue;
      var entry = entry(state, body.getUniqueId(), box, now);
      if (entry == null) continue; // Never evict an active failed contact just to retry it.
      entry.tick = now;
      if (entry.session != null) {
        if (entry.session.gate.end ? !end : !nether) { release(state, entry.session, now); continue; }
        if (observe(level, state, entry.session, body, now)) break;
        continue;
      }
      if (data != null && PortalTransform.cooling(now, data.getLong("GuidedPortalUntil"), COOLDOWN_TICKS)) {
        entry.needsExit = true; continue;
      }
      if (now < entry.errorUntil) continue;
      // Moving bodies can bypass the negative-cache delay; sweeps cover the last discovery interval.
      if (!PortalContact.scanDue(now, entry.nextProbe, box.getCenter(), entry.ownerCenter)) continue;
      entry.ownerCenter = box.getCenter();
      var sensed = sensor(level, body); box = sensed.box;
      var search = box;
      if (now >= entry.probed && now - entry.probed <= 100
          && box.getCenter().distanceTo(entry.box.getCenter()) <= (now - entry.probed) * MAX_SPEED / 20 + 2)
        search = box.minmax(entry.box);
      entry.nextProbe = now + PortalContact.scanDelay(entry.box, box);
      entry.box = box; entry.probed = now;
      BlockPos position;
      try {
        position = nether ? touching(level, search) : null;
        if (position == null && end) position = touchingEnd(level, search);
      } catch (RuntimeException failure) {
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[PhysicalPortal] discovery failed body={}", body.getUniqueId(), failure);
        entry.errorUntil = now + 100; continue;
      }
      if (position == null) { entry.needsExit = false; continue; }
      boolean endPortal = level.getBlockState(position).is(Blocks.END_PORTAL);
      Gate gate = new Gate(position.immutable(), new AABB(position), endPortal);
      Set<UUID> members = sensed.members;
      try {
        gate = gate(level, position, endPortal);
        members = Set.copyOf(TransferGroup.collect(level, body).keySet());
        // Swept discovery is not permission to enter a portal already completely passed.
        if (!inside(level, members, gate.area)) continue;
        Session existing = null;
        for (var id : members) {
          var other = state.entries.get(id);
          if (other != null && other.session != null) { existing = other.session; break; }
        }
        var session = existing != null ? existing : new Session(body.getUniqueId(), members, gate);
        bind(level, state, entry, session, now);
        observe(level, state, session, body, now);
      } catch (RuntimeException failure) {
        var session = new Session(body.getUniqueId(), members, gate);
        session.contact.block(); bind(level, state, entry, session, now);
        fail(level, body, session, failure);
      }
      break; // At most one portal discovery/assembly inspection or transfer attempt per world tick.
    }
  }

  private static Entry entry(State state, UUID id, AABB box, long now) {
    var entry = state.entries.get(id); if (entry != null) return entry;
    if (state.entries.size() >= MAX_ENTRIES) {
      var oldest = state.entries.entrySet().stream().filter(value -> value.getValue().session == null)
          .min(Comparator.comparingLong(value -> value.getValue().tick));
      if (oldest.isEmpty()) return null;
      state.entries.remove(oldest.get().getKey());
    }
    entry = new Entry(box, now); state.entries.put(id, entry); return entry;
  }

  private static void bind(ServerLevel level, State state, Entry owner, Session session, long now) {
    owner.session = session;
    var container = SubLevelContainer.getContainer(level);
    for (var id : session.members) {
      var body = container.getSubLevel(id); if (body == null || body.isRemoved()) continue;
      var member = entry(state, id, body.boundingBox().toMojang(), now);
      if (member != null) {
        if (member.needsExit) session.contact.block();
        member.session = session; member.tick = now;
      }
    }
  }

  private static void release(State state, Session session, long now) {
    session.contact.leave();
    for (var entry : state.entries.values()) if (entry.session == session) {
      entry.session = null; entry.needsExit = false; entry.nextProbe = now + SCAN_TICKS;
    }
  }

  private static boolean inside(ServerLevel level, Set<UUID> members, AABB area) {
    var container = SubLevelContainer.getContainer(level);
    for (var id : members) {
      var body = container.getSubLevel(id);
      if (body != null && !body.isRemoved() && body.boundingBox().toMojang().intersects(area)) return true;
    }
    return false;
  }

  private static boolean observe(ServerLevel level, State state, Session session, ServerSubLevel fallback, long now) {
    if (session.checked == now) return false;
    session.checked = now;
    if (!level.hasChunkAt(session.gate.anchor)) { release(state, session, now); return false; }
    var portal = level.getBlockState(session.gate.anchor);
    if (!portal.is(session.gate.end ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL)
        || !inside(level, session.members, session.gate.area)) { release(state, session, now); return false; }
    for (var id : session.members) {
      var entry = state.entries.get(id); if (entry != null && entry.session == session) entry.tick = now;
    }
    if (!session.contact.enter()) return false;
    var found = SubLevelContainer.getContainer(level).getSubLevel(session.root);
    var root = found instanceof ServerSubLevel alive && !alive.isRemoved() ? alive : fallback;
    session.attempting = true;
    try {
      attempt(level, root, session.gate);
      release(state, session, now);
    } catch (RuntimeException failure) {
      session.contact.block(); fail(level, root, session, failure);
    } finally {
      session.attempting = false;
    }
    return true;
  }

  private static void fail(ServerLevel level, ServerSubLevel root, Session session, RuntimeException failure) {
    if (!session.parked) {
      session.parked = true;
      try { PhysicalTransfer.stopAtPortal(level, root.getUniqueId()); }
      catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalPortal] cannot stop body={}", root.getUniqueId(), failure);
      }
    }
    notify(level, root.boundingBox().toMojang().getCenter(), Component.translatable("message.guidedrailways.transfer.portal_failed", UserText.error(failure)));
  }

  private static void attempt(ServerLevel level, ServerSubLevel body, Gate gate) {
    long start = LagProbe.begin();
    try {
      var group = PhysicalTransfer.portalBounds(body); var pose = body.logicalPose().position();
      var position = new Vec3(pose.x, pose.y, pose.z);
      if (gate.end) {
        var target = level.getServer().getLevel(level.dimension() == Level.END ? Level.OVERWORLD : Level.END);
        if (target == null || !level.getServer().isLevelEnabled(target)) throw error("portal_missing");
        try (var landing = new EndPortalLanding(target, group, position)) {
          PhysicalTransfer.throughPortal(level, body.getUniqueId(), target, landing.transform.destination(), 0);
          landing.commit();
        }
      } else {
        var source = opening(level, gate.anchor);
        var target = level.getServer().getLevel(level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        if (target == null || !level.getServer().isLevelEnabled(target)) throw error("portal_missing");
        double scale = DimensionType.getTeleportationScale(level.dimensionType(), target.dimensionType());
        var center = target.getWorldBorder().clampToBounds(new Vec3(source.bottomCenter().x * scale,
            source.bottomCenter().y, source.bottomCenter().z * scale));
        var exit = target.getPortalForcer().findClosestPortalPosition(center, target.dimension() == Level.NETHER, target.getWorldBorder());
        if (exit.isEmpty()) {
          var created = target.getPortalForcer().createPortal(center, source.widthX() ? Direction.Axis.X : Direction.Axis.Z);
          if (created.isEmpty()) throw error("portal_missing");
          exit = Optional.of(created.get().minCorner);
        }
        target.getChunkAt(exit.get());
        var speed = SubLevelPhysicsSystem.get(level).getPipeline().getLinearVelocity(body, new Vector3d());
        var transform = PortalTransform.passage(source, opening(target, exit.get()), group, position, new Vec3(speed.x, speed.y, speed.z));
        PhysicalTransfer.throughPortal(level, body.getUniqueId(), target, transform.destination(), transform.turns());
      }
      notify(level, position, Component.translatable("message.guidedrailways.transfer.portal_done"));
    } finally { LagProbe.end(LagProbe.Stage.PORTAL_ATTEMPT, start); }
  }

  private static Sensor sensor(ServerLevel level, ServerSubLevel body) {
    var box = body.boundingBox().toMojang(); var container = SubLevelContainer.getContainer(level);
    var members = new HashSet<UUID>(); members.add(body.getUniqueId());
    for (var holder : body.getPlot().getLoadedChunks()) for (var be : holder.getChunk().getBlockEntities().values())
      if (be instanceof MountEntity mount && mount.bogieId != null
          && container.getSubLevel(mount.bogieId) instanceof ServerSubLevel lower && !lower.isRemoved()) {
        box = box.minmax(lower.boundingBox().toMojang()); members.add(lower.getUniqueId());
      }
    return new Sensor(box, Set.copyOf(members));
  }

  private static Gate gate(ServerLevel level, BlockPos position, boolean end) {
    if (end) {
      var rect = BlockUtil.getLargestRectangleAround(position, Direction.Axis.X, 21, Direction.Axis.Z, 21,
          pos -> level.getBlockState(pos).is(Blocks.END_PORTAL));
      var min = rect.minCorner;
      return new Gate(position.immutable(), new AABB(min.getX(), min.getY() + .75, min.getZ(),
          min.getX() + rect.axis1Size, min.getY() + .875, min.getZ() + rect.axis2Size), true);
    }
    var door = opening(level, position); var base = door.bottomCenter();
    return new Gate(position.immutable(), door.widthX()
        ? new AABB(base.x - door.width() / 2.0, base.y, base.z - .5, base.x + door.width() / 2.0, base.y + door.height(), base.z + .5)
        : new AABB(base.x - .5, base.y, base.z - door.width() / 2.0, base.x + .5, base.y + door.height(), base.z + door.width() / 2.0), false);
  }

  private static IllegalArgumentException error(String key) {
    return new IllegalArgumentException("message.guidedrailways.transfer." + key);
  }

  private static BlockPos touching(ServerLevel level, AABB box) {
    int radius = Math.min(64, (int) Math.ceil(Math.max(box.getXsize(), box.getZsize()) / 2) + 2);
    try (var points = level.getPoiManager().getInSquare(holder -> holder.is(PoiTypes.NETHER_PORTAL),
        BlockPos.containing(box.getCenter()), radius, PoiManager.Occupancy.ANY)) {
      return points.limit(MAX_PORTAL_POINTS).map(PoiRecord::getPos).filter(level::hasChunkAt)
          .filter(pos -> box.intersects(new AABB(pos)))
          .filter(pos -> level.getBlockState(pos).is(Blocks.NETHER_PORTAL)).findFirst().orElse(null);
    }
  }

  private static BlockPos touchingEnd(ServerLevel level, AABB box) {
    int minX = net.minecraft.util.Mth.floor(box.minX), maxX = net.minecraft.util.Mth.floor(box.maxX);
    int minZ = net.minecraft.util.Mth.floor(box.minZ), maxZ = net.minecraft.util.Mth.floor(box.maxZ);
    int minY = Math.max(level.getMinBuildHeight(), net.minecraft.util.Mth.floor(box.minY));
    int maxY = Math.min(level.getMaxBuildHeight() - 1, net.minecraft.util.Mth.floor(box.maxY));
    if ((long) ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1) > PhysicalTransfer.MAX_CHUNKS) return null;
    int examined = 0;
    for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
      var chunk = level.getChunkSource().getChunkNow(cx, cz);
      if (chunk == null) continue;
      for (int section = minY >> 4; section <= maxY >> 4; section++) {
        var states = chunk.getSection(level.getSectionIndexFromSectionY(section));
        if (!states.maybeHas(state -> state.is(Blocks.END_PORTAL))) continue;
        for (int y = Math.max(minY, section << 4); y <= Math.min(maxY, (section << 4) + 15); y++)
          for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++)
            for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
              if (++examined > 8192) return null;
              if (states.getBlockState(x & 15, y & 15, z & 15).is(Blocks.END_PORTAL)
                  && box.intersects(new AABB(x, y + .75, z, x + 1, y + .875, z + 1))) return new BlockPos(x, y, z);
            }
      }
    }
    return null;
  }

  private static PortalTransform.Opening opening(ServerLevel level, BlockPos position) {
    var state = level.getBlockState(position);
    if (!state.is(Blocks.NETHER_PORTAL)) throw error("portal_missing");
    var axis = state.getValue(NetherPortalBlock.AXIS);
    if (!new PortalShape(level, position, axis).isComplete()) throw error("portal_missing");
    var rect = BlockUtil.getLargestRectangleAround(position, axis, 21, Direction.Axis.Y, 21,
        pos -> level.getBlockState(pos).equals(state));
    var min = rect.minCorner;
    return new PortalTransform.Opening(axis == Direction.Axis.X
        ? new Vec3(min.getX() + rect.axis1Size / 2.0, min.getY(), min.getZ() + .5)
        : new Vec3(min.getX() + .5, min.getY(), min.getZ() + rect.axis1Size / 2.0),
        axis == Direction.Axis.X, rect.axis1Size, rect.axis2Size);
  }

  private static void notify(ServerLevel level, Vec3 position, Component message) {
    for (var player : level.players()) if (player.position().distanceToSqr(position) <= 32 * 32)
      player.displayClientMessage(message, false);
  }
}
