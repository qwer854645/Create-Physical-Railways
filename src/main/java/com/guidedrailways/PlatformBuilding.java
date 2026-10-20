package com.guidedrailways;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Only refine the supporting bogie's proxy cells; every unrelated Sable placement check stays
 * intact.
 */
public final class PlatformBuilding {
  private static final java.util.concurrent.atomic.AtomicBoolean REPORTED =
      new java.util.concurrent.atomic.AtomicBoolean();

  public static double refine(
      double depthSquared,
      BlockPlaceContext context,
      SubLevel other,
      BlockPos cell,
      Vector3d candidateCentre,
      Quaterniond candidateRotation) {
    if (depthSquared <= .05
        || !Double.isFinite(depthSquared)
        || other == null
        || context.getClickedFace() != Direction.UP
        || context.replacingClickedOnBlock()) return depthSquared;
    var level = context.getLevel();
    var mountPos = context.getClickedPos().below();
    if (!(level.getBlockEntity(mountPos) instanceof MountEntity mount)
        || mount.bogieId == null
        || !mount.bogieId.equals(other.getUniqueId())) return depthSquared;
    var carrier = SableCompanion.INSTANCE.getContaining(level, mountPos);
    if (carrier == null
        || carrier == other
        || SableCompanion.INSTANCE.getContaining(level, context.getClickedPos()) != carrier)
      return depthSquared;
    var state = level.getBlockState(cell);
    if (!state.is(Content.BOGIE.get()) && !state.is(Content.COLLISION.get())) return depthSquared;
    boolean intersects =
        PlacementGeometry.intersects(
            state.getCollisionShape(level, cell),
            cell,
            other.logicalPose(),
            candidateCentre,
            candidateRotation);
    if (REPORTED.compareAndSet(false, true))
      org.slf4j.LoggerFactory.getLogger("guidedrailways")
          .info("[MountPlacement] precise supporting-bogie placement check active");
    return intersects ? depthSquared : 0;
  }

  private PlatformBuilding() {}
}
