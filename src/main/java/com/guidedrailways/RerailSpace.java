package com.guidedrailways;

import com.simibubi.create.content.trains.track.ITrackBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Read-only preflight of the entire relocation, using physical shapes rather than body AABBs. */
final class RerailSpace {
  record Box(Vector3d centre, Vector3d size, Quaterniond rotation, AABB bounds, boolean monorail) {}

  private static final class Budget {
    int remaining = 1_000_000;

    void use() {
      if (--remaining < 0)
        throw new IllegalArgumentException("message.guidedrailways.rail.space_complex");
    }
  }

  static final class Overlap {
    final LevelReusedVectors scratch = new LevelReusedVectors();
    final OrientedBoundingBox3d a = new OrientedBoundingBox3d(scratch);
    final OrientedBoundingBox3d b = new OrientedBoundingBox3d(scratch);
    final Vector3d penetration = new Vector3d();

    boolean test(Box first, Box second) {
      if (!first.bounds.intersects(second.bounds)) return false;
      a.set(first.centre, first.size, first.rotation);
      b.set(second.centre, second.size, second.rotation);
      // Permit touching surfaces and solver-sized numerical noise, not deep intersections.
      return OrientedBoundingBox3d.sat(a, b, penetration).lengthSquared() > 1e-6;
    }
  }

  static Box box(AABB local, Pose3d pose, boolean monorail) {
    var centre =
        new Vector3d(
            (local.minX + local.maxX) * .5,
            (local.minY + local.maxY) * .5,
            (local.minZ + local.maxZ) * .5);
    pose.transformPosition(centre);
    var size =
        new Vector3d(local.getXsize(), local.getYsize(), local.getZsize())
            .mul(pose.scale())
            .absolute();
    var rotation = new Quaterniond(pose.orientation());
    var radius = new Vector3d();
    for (var axis :
        List.of(
            new Vector3d(size.x * .5, 0, 0),
            new Vector3d(0, size.y * .5, 0),
            new Vector3d(0, 0, size.z * .5))) radius.add(axis.rotate(rotation).absolute());
    return new Box(
        centre,
        size,
        rotation,
        new AABB(
            centre.x - radius.x,
            centre.y - radius.y,
            centre.z - radius.z,
            centre.x + radius.x,
            centre.y + radius.y,
            centre.z + radius.z),
        monorail);
  }

  static void check(ServerLevel level, Map<ServerSubLevel, Pose3d> targets) {
    var budget = new Budget();
    var overlap = new Overlap();
    var moving = new ArrayList<Box>();
    for (var target : targets.entrySet()) {
      if (target.getKey().isRemoved()
          || !target.getValue().position().isFinite()
          || !target.getValue().orientation().isFinite()
          || !target.getValue().scale().isFinite())
        throw new IllegalStateException("message.guidedrailways.body.changed");
      moving.addAll(shapes(target.getKey(), target.getValue(), budget));
    }
    if (moving.isEmpty()) throw new IllegalStateException("message.guidedrailways.body.changed");
    AABB bounds = moving.getFirst().bounds;
    for (var part : moving) bounds = bounds.minmax(part.bounds);
    var container = SubLevelContainer.getContainer(level);
    var obstacles = new HashMap<BlockPos, List<Box>>();
    for (var other : container.queryIntersecting(new BoundingBox3d(bounds))) {
      if (!(other instanceof ServerSubLevel body) || body.isRemoved() || targets.containsKey(body))
        continue;
      var pose = SubLevelPhysicsSystem.get(level).getPipeline().readPose(body, new Pose3d());
      for (var part : shapes(body, pose, budget))
        if (part.bounds.intersects(bounds))
          for (var key : cells(part.bounds, 4)) {
            budget.use();
            obstacles.computeIfAbsent(key.immutable(), k -> new ArrayList<>()).add(part);
          }
    }
    var terrain = new HashMap<BlockPos, List<Box>>();
    for (var part : moving) {
      var area = part.bounds;
      if (!level
              .getWorldBorder()
              .isWithinBounds(BlockPos.containing(area.minX, area.minY, area.minZ))
          || !level
              .getWorldBorder()
              .isWithinBounds(BlockPos.containing(area.maxX, area.maxY, area.maxZ)))
        throw new IllegalArgumentException("message.guidedrailways.placement.border");
      if (area.minY < level.getMinBuildHeight() || area.maxY > level.getMaxBuildHeight())
        throw new IllegalArgumentException("message.guidedrailways.rail.space_blocked");
      // Include neighbouring cells for shapes such as fences that extend beyond their own cell.
      for (var pos : cells(area.inflate(1), 1)) {
        budget.use();
        if (!level.hasChunkAt(pos))
          throw new IllegalArgumentException("message.guidedrailways.rail.space_unloaded");
        var state = level.getBlockState(pos);
        if (part.monorail && monorailTrack(state)) continue;
        var boxes =
            terrain.computeIfAbsent(
                pos.immutable(),
                key -> {
                  var shape = state.getCollisionShape(level, key);
                  return shape.toAabbs().stream()
                      .map(b -> box(b.move(key), new Pose3d(), false))
                      .toList();
                });
        for (var obstacle : boxes) {
          budget.use();
          if (overlap.test(part, obstacle)) blocked();
        }
      }
      var seen = Collections.newSetFromMap(new IdentityHashMap<Box, Boolean>());
      for (var key : cells(area, 4))
        for (var obstacle : obstacles.getOrDefault(key, List.of())) {
          budget.use();
          if (seen.add(obstacle) && overlap.test(part, obstacle)) blocked();
        }
    }
  }

  private static void blocked() {
    throw new IllegalArgumentException("message.guidedrailways.rail.space_blocked");
  }

  private static boolean monorailTrack(BlockState state) {
    return state.getBlock() instanceof ITrackBlock track
        && RailGauge.MONORAIL.accepts(track.getMaterial().trackType.id.toString());
  }

  private static Iterable<BlockPos> cells(AABB bounds, int size) {
    return BlockPos.betweenClosed(
        (int) Math.floor(bounds.minX / size),
        (int) Math.floor(bounds.minY / size),
        (int) Math.floor(bounds.minZ / size),
        (int) Math.floor(bounds.maxX / size),
        (int) Math.floor(bounds.maxY / size),
        (int) Math.floor(bounds.maxZ / size));
  }

  private static List<Box> shapes(ServerSubLevel body, Pose3d pose, Budget budget) {
    var out = new ArrayList<Box>();
    var plot = body.getPlot();
    var bounds = plot.getBoundingBox();
    var getter = plot.getEmbeddedLevelAccessor();
    if (plot.getLoadedChunks().isEmpty())
      throw new IllegalArgumentException("message.guidedrailways.rail.space_unloaded");
    for (var holder : plot.getLoadedChunks()) {
      budget.use();
      var chunk = holder.getChunk();
      int x = chunk.getPos().x, z = chunk.getPos().z;
      for (int section = 0; section < chunk.getSections().length; section++) {
        var data = chunk.getSection(section);
        if (data.hasOnlyAir()) continue;
        int y = chunk.getSectionYFromSectionIndex(section) << 4;
        if (y > bounds.maxY() || y + 15 < bounds.minY()) continue;
        for (var pos :
            BlockPos.betweenClosed(
                Math.max(bounds.minX(), x << 4),
                Math.max(bounds.minY(), y),
                Math.max(bounds.minZ(), z << 4),
                Math.min(bounds.maxX(), (x << 4) + 15),
                Math.min(bounds.maxY(), y + 15),
                Math.min(bounds.maxZ(), (z << 4) + 15))) {
          budget.use();
          var state = chunk.getBlockState(pos);
          if (state.isAir()) continue;
          var shape =
              state.getBlock() instanceof BlockSubLevelCollisionShape custom
                  ? custom.getSubLevelCollisionShape(getter, state)
                  : state.getCollisionShape(getter, pos);
          boolean monorail =
              state.getBlock() instanceof BogieBlock
                  ? BogieBlock.gauge(state) == RailGauge.MONORAIL
                  : state.getBlock() instanceof CollisionPartBlock
                      && BogieCollision.shape(state.getValue(CollisionPartBlock.SHAPE))
                              .variant()
                              .gauge
                          == RailGauge.MONORAIL;
          for (var box : shape.toAabbs()) {
            budget.use();
            out.add(box(box.move(pos), pose, monorail));
          }
        }
      }
    }
    return out;
  }

  private RerailSpace() {}
}
