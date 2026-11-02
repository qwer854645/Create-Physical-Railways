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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

/** Bounded POI / section-palette detection for vanilla world portals. */
final class PhysicalPortals {
  static final int COOLDOWN_TICKS = 100, MAX_ENTRIES = 256, EXPIRY_TICKS = 600;
  static final double MAX_SPEED = 16;
  private static final int VISITS_PER_TICK = 8, RETRY_TICKS = 100, MAX_PORTAL_POINTS = 1024;
  private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
  private static final class Entry {
    AABB box;
    long tick, retry;
    Entry(AABB box, long tick) { this.box = box; this.tick = tick; }
  }
  private static final class State {
    int cursor;
    final Map<UUID, Entry> entries = new HashMap<>();
  }
  private PhysicalPortals() {}

  static void unload(ServerLevel level) { STATES.remove(level); }

  static void tick(ServerLevel level) {
    long now = level.getServer().overworld().getGameTime();
    var previous = STATES.get(level);
    if (previous != null) previous.entries.values().removeIf(entry -> now < entry.tick || now - entry.tick > EXPIRY_TICKS);
    boolean nether = TrainConfig.NETHER_PORTAL_TRANSFERS.get() && (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER);
    boolean end = TrainConfig.END_PORTAL_TRANSFERS.get() && (level.dimension() == Level.OVERWORLD || level.dimension() == Level.END);
    if (!nether && !end) return;
    var container = SubLevelContainer.getContainer(level);
    if (container == null || SubLevelPhysicsSystem.currentlySteppingSystem != null) return;
    var bodies = List.copyOf(container.getAllSubLevels());
    if (bodies.isEmpty()) { STATES.remove(level); return; }
    var state = STATES.computeIfAbsent(level, ignored -> new State());
    for (int visit = 0; visit < Math.min(VISITS_PER_TICK, bodies.size()); visit++) {
      var body = bodies.get(Math.floorMod(state.cursor++, bodies.size()));
      if (body.isRemoved() || level.getBlockState(body.getPlot().getCenterBlock()).is(Content.BOGIE.get())) continue;
      var box = contactBounds(level, body);
      double volume = box.getXsize() * box.getYsize() * box.getZsize();
      if (!Double.isFinite(volume) || volume <= 0 || volume > PhysicalTransfer.MAX_VOLUME) continue;
      var entry = state.entries.get(body.getUniqueId());
      var search = box;
      if (entry != null && now >= entry.tick && now - entry.tick <= 100
          && box.getCenter().distanceTo(entry.box.getCenter()) <= (now - entry.tick) * MAX_SPEED / 20 + 2)
        search = box.minmax(entry.box);
      if (entry == null) {
        if (state.entries.size() >= MAX_ENTRIES) {
          var oldest = state.entries.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().tick));
          oldest.ifPresent(value -> state.entries.remove(value.getKey()));
        }
        entry = new Entry(box, now); state.entries.put(body.getUniqueId(), entry);
      }
      entry.box = box; entry.tick = now;
      var data = body.getUserDataTag();
      if (now < entry.retry || data != null
          && PortalTransform.cooling(now, data.getLong("GuidedPortalUntil"), COOLDOWN_TICKS)) continue;
      BlockPos portal;
      boolean endPortal;
      try {
        portal = nether ? touching(level, search) : null;
        if (portal == null && end) portal = touchingEnd(level, search);
        endPortal = portal != null && level.getBlockState(portal).is(Blocks.END_PORTAL);
      }
      catch (RuntimeException failure) {
        entry.retry = now + RETRY_TICKS;
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[PhysicalPortal] detection failed body={}", body.getUniqueId(), failure);
        continue;
      }
      if (portal == null) continue;
      entry.retry = now + RETRY_TICKS;
      var position = box.getCenter();
      try {
        if (endPortal) {
          var target = level.getServer().getLevel(level.dimension() == Level.END ? Level.OVERWORLD : Level.END);
          if (target == null || !level.getServer().isLevelEnabled(target)) throw error("portal_missing");
          var group = PhysicalTransfer.portalBounds(body); var pose = body.logicalPose().position();
          try (var landing = new EndPortalLanding(target, group, new Vec3(pose.x, pose.y, pose.z))) {
            PhysicalTransfer.throughPortal(level, body.getUniqueId(), target, landing.transform.destination(), 0);
            landing.commit();
          }
          notify(level, position, Component.translatable("message.guidedrailways.transfer.portal_done"));
          break;
        }
        var source = opening(level, portal);
        var group = PhysicalTransfer.portalBounds(body);
        if (!source.fits(group)) throw error("portal_fit");
        var target = level.getServer().getLevel(level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        if (target == null || !level.getServer().isLevelEnabled(target)) throw error("portal_missing");
        double scale = DimensionType.getTeleportationScale(level.dimensionType(), target.dimensionType());
        var scaled = new Vec3(source.bottomCenter().x * scale, source.bottomCenter().y,
            source.bottomCenter().z * scale);
        var center = target.getWorldBorder().clampToBounds(scaled);
        var exit = target.getPortalForcer().findClosestPortalPosition(center, target.dimension() == Level.NETHER, target.getWorldBorder());
        if (exit.isEmpty()) {
          var created = target.getPortalForcer().createPortal(center, source.widthX() ? Direction.Axis.X : Direction.Axis.Z);
          if (created.isEmpty()) throw error("portal_missing");
          exit = Optional.of(created.get().minCorner);
        }
        target.getChunkAt(exit.get());
        var destination = opening(target, exit.get());
        var pose = body.logicalPose().position();
        var speed = SubLevelPhysicsSystem.get(level).getPipeline().getLinearVelocity(body, new Vector3d());
        var transform = PortalTransform.passage(source, destination, group,
            new Vec3(pose.x, pose.y, pose.z), new Vec3(speed.x, speed.y, speed.z));
        PhysicalTransfer.throughPortal(level, body.getUniqueId(), target, transform.destination(), transform.turns());
        notify(level, position, Component.translatable("message.guidedrailways.transfer.portal_done"));
      } catch (RuntimeException failure) {
        try { PhysicalTransfer.stopAtPortal(level, body.getUniqueId()); }
        catch (RuntimeException cleanup) {
          failure.addSuppressed(cleanup);
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalPortal] cannot stop body={}", body.getUniqueId(), failure);
        }
        notify(level, position, Component.translatable("message.guidedrailways.transfer.portal_failed", UserText.error(failure)));
      }
      break; // At most one expensive portal resolution/transfer attempt per world tick.
    }
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

  private static AABB contactBounds(ServerLevel level, ServerSubLevel body) {
    var box = body.boundingBox().toMojang(); var container = SubLevelContainer.getContainer(level);
    for (var holder : body.getPlot().getLoadedChunks()) for (var be : holder.getChunk().getBlockEntities().values()) if (be instanceof MountEntity mount && mount.bogieId != null
        && container.getSubLevel(mount.bogieId) instanceof ServerSubLevel lower && !lower.isRemoved())
      box = box.minmax(lower.boundingBox().toMojang());
    return box;
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
