package com.guidedrailways;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Precise voxel-box placement overlap; unlike a whole cell, empty parts do not obstruct building.
 */
final class PlacementGeometry {
  private static final class Workspace {
    final LevelReusedVectors scratch = new LevelReusedVectors();
    final OrientedBoundingBox3d candidate = new OrientedBoundingBox3d(scratch);
    final OrientedBoundingBox3d obstacle = new OrientedBoundingBox3d(scratch);
    final Vector3d centre = new Vector3d(), size = new Vector3d(), penetration = new Vector3d();
  }

  private static final ThreadLocal<Workspace> WORK = ThreadLocal.withInitial(Workspace::new);

  static boolean intersects(
      VoxelShape shape,
      BlockPos cell,
      Pose3dc pose,
      Vector3dc candidateCentre,
      Quaterniondc candidateRotation) {
    var w = WORK.get();
    w.candidate.set(candidateCentre, w.size.set(1), candidateRotation);
    for (var box : shape.toAabbs()) {
      w.centre.set(
          cell.getX() + (box.minX + box.maxX) * .5,
          cell.getY() + (box.minY + box.maxY) * .5,
          cell.getZ() + (box.minZ + box.maxZ) * .5);
      pose.transformPosition(w.centre);
      w.size.set(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ);
      w.obstacle.set(w.centre, w.size, pose.orientation());
      if (OrientedBoundingBox3d.sat(w.candidate, w.obstacle, w.penetration).lengthSquared() > 1e-12)
        return true;
    }
    return false;
  }

  private PlacementGeometry() {}
}
