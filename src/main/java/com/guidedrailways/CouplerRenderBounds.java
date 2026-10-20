package com.guidedrailways;

import dev.ryanhcode.sable.companion.*;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/** Plot-space bounds: Sable transforms these to the world before testing the view frustum. */
final class CouplerRenderBounds {
  static AABB get(CouplerEndpoint e) {
    var pos = e.getBlockPos();
    double radius = 2.5;
    var own =
        e.getLevel() == null ? null : SableCompanion.INSTANCE.getContaining(e.getLevel(), pos);
    if (e.visualConnected
        && own instanceof ClientSubLevelAccess a
        && e.visualPeerPos != null
        && e.getLevel().hasChunkAt(e.visualPeerPos)) {
      var peer = CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos));
      var other =
          peer == null
              ? null
              : SableCompanion.INSTANCE.getContaining(e.getLevel(), peer.getBlockPos());
      if (peer != null
          && peer.endpointId.equals(e.visualPeerId)
          && other instanceof ClientSubLevelAccess b) {
        for (int partial = 0; partial <= 1; partial++) {
          var anchor =
              new Vector3d(peer.anchor)
                  .add(
                      peer.getBlockPos().getX(),
                      peer.getBlockPos().getY(),
                      peer.getBlockPos().getZ());
          var world = b.renderPose(partial).transformPosition(anchor, new Vector3d());
          var local =
              a.renderPose(partial)
                  .transformPositionInverse(world, new Vector3d())
                  .sub(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
          if (local.isFinite()) radius = Math.max(radius, local.length() + 2);
        }
      }
    }
    return new AABB(
        pos.getX() + .5 - radius,
        pos.getY() + .5 - radius,
        pos.getZ() + .5 - radius,
        pos.getX() + .5 + radius,
        pos.getY() + .5 + radius,
        pos.getZ() + .5 + radius);
  }
}
