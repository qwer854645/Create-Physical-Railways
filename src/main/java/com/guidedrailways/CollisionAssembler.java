package com.guidedrailways;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Preflight the entire current layout and roll back every attempted block edit on failure. */
final class CollisionAssembler {
  private static final String CATALOG = "shape_catalog_v1";

  private record Motion(Pose3d pose, Vector3d reference, Vector3d velocity, Vector3d angular) {}

  private CollisionAssembler() {}

  static boolean populate(ServerSubLevel body, boolean refreshMass) {
    var level = body.getLevel();
    var centre = body.getPlot().getCenterBlock();
    var variant = BogieBlock.variant(level.getBlockState(centre));
    var saved = body.getUserDataTag();
    if (saved != null
        && CATALOG.equals(saved.getString("CollisionCatalog"))
        && saved.getInt("CollisionProfile") == variant.ordinal()) return true;
    var tag = saved == null ? new CompoundTag() : saved.copy();

    var targets = new ArrayList<BlockAssembly.Target<BlockPos, BlockState>>();
    for (var cell : BogieCollision.cells(variant)) {
      if (cell.part() == BogieCollision.CENTRE_PART) continue;
      var pos = centre.offset(cell.x(), cell.y(), cell.z()).immutable();
      var after =
          Content.COLLISION
              .get()
              .defaultBlockState()
              .setValue(CollisionPartBlock.SHAPE, BogieCollision.shapeId(variant, cell.part()));
      targets.add(new BlockAssembly.Target<>(pos, after));
    }

    var plan =
        BlockAssembly.plan(
            targets,
            level::getBlockState,
            state -> state.isAir() || state.is(Content.COLLISION.get()));
    if (plan.isEmpty()) return false;
    var edits = new ArrayList<>(plan.get());
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var motion =
        refreshMass
            ? new Motion(
                pipe.readPose(body, new Pose3d()),
                new Vector3d(body.logicalPose().rotationPoint()),
                pipe.getLinearVelocity(body, new Vector3d()),
                pipe.getAngularVelocity(body, new Vector3d()))
            : null;
    // Include mass refresh in the transaction, even for a central-only invisible bogey.
    edits.add(new BlockAssembly.Edit<>(null, null, null));
    try {
      MoveBatch.commit(
          edits,
          edit -> {
            if (edit.key() != null && !level.getBlockState(edit.key()).equals(edit.before()))
              throw new IllegalStateException("Collision target changed during assembly");
          },
          edit -> {
            if (edit.key() == null) {
              if (motion != null) rebuild(body, motion);
            } else if (!edit.before().equals(edit.after())) put(body, edit.key(), edit.after());
          },
          edit -> {
            if (edit.key() != null) put(body, edit.key(), edit.before());
          });
    } catch (RuntimeException failure) {
      if (motion != null) {
        try {
          rebuild(body, motion);
        } catch (RuntimeException restoreFailure) {
          failure.addSuppressed(restoreFailure);
        }
      }
      throw failure;
    }
    tag.putString("CollisionCatalog", CATALOG);
    tag.putInt("CollisionProfile", variant.ordinal());
    body.setUserDataTag(tag);
    if (refreshMass) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[BogieMass] body={} mass={} layout={}",
              body.getUniqueId(),
              body.getMassTracker().getMass(),
              CATALOG);
    }
    return true;
  }

  private static void put(ServerSubLevel body, BlockPos pos, BlockState state) {
    var level = body.getLevel();
    var chunk = level.getChunkAt(pos);
    var before = chunk.getBlockState(pos);
    chunk.setBlockState(pos, state, true);
    SubLevelAssemblyHelper.markAndNotifyBlock(level, pos, chunk, before, state, 3, 512);
  }

  private static void rebuild(ServerSubLevel body, Motion before) {
    var pipe = SubLevelPhysicsSystem.get(body.getLevel()).getPipeline();
    body.buildMassTracker();
    body.updateMergedMassData(1);
    var com = new Vector3d(body.getMassTracker().getCenterOfMass());
    var shift = new Vector3d(com).sub(before.reference()).rotate(before.pose().orientation());
    body.logicalPose().rotationPoint().set(com);
    pipe.onStatsChanged(body);
    pipe.teleport(
        body, new Vector3d(before.pose().position()).add(shift), before.pose().orientation());
    pipe.resetVelocity(body);
    pipe.addLinearAndAngularVelocity(
        body, new Vector3d(before.angular()).cross(shift).add(before.velocity()), before.angular());
    body.updateLastPose();
    body.updateBoundingBox();
  }
}
