package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** A bounded, reversible landing platform; existing terrain and buildings are never excavated. */
final class EndPortalLanding implements AutoCloseable {
  static final int MAX_PLATFORM_BLOCKS = 2048;
  final PortalTransform transform;
  private final ServerLevel level;
  private final List<BlockPos> placed = new ArrayList<>();
  private boolean committed;

  EndPortalLanding(ServerLevel level, AABB group, Vec3 anchor) {
    this.level = level;
    boolean end = level.dimension() == Level.END;
    var spawn = level.getSharedSpawnPos();
    double x = end ? 100.5 : spawn.getX() + 16.5, z = end ? .5 : spawn.getZ() + .5;
    var horizontal = new PortalTransform(anchor, new Vec3(x, anchor.y, z), 0);
    var area = horizontal.bounds(group);
    int minX = (int) Math.floor(area.minX) - 1, maxX = (int) Math.floor(area.maxX) + 1;
    int minZ = (int) Math.floor(area.minZ) - 1, maxZ = (int) Math.floor(area.maxZ) + 1;
    long blocks = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
    if (blocks <= 0 || blocks > MAX_PLATFORM_BLOCKS) throw error("limit");
    var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
    PhysicalTransfer.loadArea(level, new AABB(minX, 49, minZ, maxX + 1, 50, maxZ + 1), chunks);
    int floor = 49;
    if (!end) for (int px = minX; px <= maxX; px++) for (int pz = minZ; pz <= maxZ; pz++)
      floor = Math.max(floor, level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz) + 1);
    transform = new PortalTransform(anchor, new Vec3(x, anchor.y + floor + 1.05 - group.minY, z), 0);
    var landing = transform.bounds(group);
    if (floor < level.getMinBuildHeight() || landing.maxY > level.getMaxBuildHeight()
        || !level.getWorldBorder().isWithinBounds(new AABB(minX, floor, minZ, maxX + 1, floor + 1, maxZ + 1))) throw error("destination");
    var platform = new AABB(minX, floor, minZ, maxX + 1, floor + 1, maxZ + 1);
    if (!level.getEntities(null, platform).isEmpty()
        || dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getAllSubLevels().stream()
            .anyMatch(body -> !body.isRemoved() && body.boundingBox().toMojang().intersects(platform))) throw error("blocked");
    // Check every floor cell before placing anything. Only air is changed; obsidian is retained.
    var positions = new ArrayList<BlockPos>();
    for (int px = minX; px <= maxX; px++) for (int pz = minZ; pz <= maxZ; pz++) {
      var pos = new BlockPos(px, floor, pz); var state = level.getBlockState(pos);
      if (!state.isAir() && !state.is(Blocks.OBSIDIAN)) throw error("blocked");
      if (state.isAir()) positions.add(pos);
    }
    try {
      for (var pos : positions) {
        // Preserve cave/void air on rollback as well as ordinary air.
        previous.put(pos, level.getBlockState(pos));
        placed.add(pos);
        if (!level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 2)) throw error("blocked");
      }
    } catch (RuntimeException failure) { close(); throw failure; }
  }

  private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> previous = new HashMap<>();
  void commit() { committed = true; }
  @Override public void close() {
    if (committed) return;
    for (var pos : placed) if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) level.setBlock(pos, previous.get(pos), 2);
  }
  private static IllegalArgumentException error(String key) { return new IllegalArgumentException("message.guidedrailways.transfer." + key); }
}
