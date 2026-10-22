package com.guidedrailways;

import static com.guidedrailways.CouplerCoordinates.*;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Adapts assembly callbacks to the endpoint's saved handoff; owns no runtime handoff maps. */
final class CouplerTransfers {
  static boolean moving(ServerLevel level, CouplerEndpoint e) {
    return e.handoff != null
        && e.handoff.protectsSource(
            e.endpointId, level.dimension().location().toString(), e.getBlockPos().asLong());
  }

  static void capture(
      ServerLevel from, ServerLevel to, BlockPos oldPos, BlockPos newPos, CouplerEndpoint e) {
    if (from != to) {
      e.handoff = null; // Cross-dimension connections have already been explicitly disconnected.
      e.setChanged();
      return;
    }
    var pending = e.handoff;
    var dimension = from.dimension().location().toString();
    Vector3d anchor = null;
    Quaterniond frame = null;
    // Preserve world geometry when a not-yet-ready destination moves again.
    if (pending == null || !pending.matches(e.endpointId, dimension, oldPos.asLong())) {
      var b = body(from, e);
      if (b != null && e.hasGeometry) {
        anchor = VehiclePhysics.world(b, blockOrigin(e).add(e.anchor));
        frame =
            new Quaterniond(
                    SubLevelPhysicsSystem.get(from)
                        .getPipeline()
                        .readPose(b, new Pose3d())
                        .orientation())
                .mul(e.frame);
      }
    }
    e.handoff =
        CouplerHandoff.begin(
            pending,
            e.endpointId,
            dimension,
            oldPos.asLong(),
            newPos.asLong(),
            anchor,
            frame,
            e.restLength);
    e.setChanged();
  }

  static boolean restore(ServerLevel level, CouplerEndpoint e) {
    var pending = e.handoff;
    if (pending == null) return true;
    var dimension = level.dimension().location().toString();
    if (!pending.matches(e.endpointId, dimension, e.getBlockPos().asLong())) {
      // A failed source move or copied pending endpoint cannot authorize another destination.
      e.handoff = null;
      e.setChanged();
      return true;
    }
    var b = body(level, e);
    var result =
        pending.commit(
            CouplerSavedData.get(level),
            e.endpointId,
            dimension,
            e.getBlockPos(),
            b != null
                && b.getMassTracker().getMass() > 0
                && SubLevelPhysicsSystem.get(level) != null);
    if (result == CouplerHandoff.Result.WAITING) return false;
    if (result == CouplerHandoff.Result.READY) {
      e.handoff = pending.ownershipCommitted();
      e.setChanged(); // Persist the committed source before a later restore or move.
      if (pending.worldAnchor() != null)
        e.geometry(
            local(b, pending.worldAnchor()).sub(blockOrigin(e)),
            localFrame(b, pending.worldFrame()),
            pending.length());
      else {
        e.hasGeometry = false;
        e.setChanged();
      }
    }
    e.handoff = null;
    e.setChanged();
    return true;
  }

  private CouplerTransfers() {}
}
