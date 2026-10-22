package com.guidedrailways;

import static com.guidedrailways.CouplerCoordinates.*;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Temporary plot-move handoff; saved links stay owned by the connection registry. */
final class CouplerTransfers {
  private record Transfer(
      UUID endpoint,
      Vector3d anchor,
      Quaterniond frame,
      boolean geometry,
      double length,
      GlobalPos source,
      long expires) {}

  private static final Map<GlobalPos, Transfer> TRANSFERS = new HashMap<>();

  private record Moving(UUID endpoint, long expires) {}

  private static final Map<GlobalPos, Moving> MOVING = new HashMap<>();

  static boolean moving(ServerLevel level, CouplerEndpoint e) {
    var moving = MOVING.get(GlobalPos.of(level.dimension(), e.getBlockPos()));
    return moving != null
        && moving.expires >= level.getGameTime()
        && moving.endpoint.equals(e.endpointId);
  }

  static void capture(
      ServerLevel from, ServerLevel to, BlockPos oldPos, BlockPos newPos, CouplerEndpoint e) {
    var b = body(from, e);
    Vector3d worldAnchor = null;
    Quaterniond worldFrame = null;
    if (b != null && e.hasGeometry) {
      worldAnchor = VehiclePhysics.world(b, blockOrigin(e).add(e.anchor));
      worldFrame =
          new Quaterniond(
                  SubLevelPhysicsSystem.get(from)
                      .getPipeline()
                      .readPose(b, new Pose3d())
                      .orientation())
              .mul(e.frame);
    }
    var source = GlobalPos.of(from.dimension(), oldPos.immutable());
    MOVING.put(source, new Moving(e.endpointId, from.getGameTime() + 200));
    TRANSFERS.put(
        GlobalPos.of(to.dimension(), newPos.immutable()),
        new Transfer(
            e.endpointId,
            worldAnchor,
            worldFrame,
            e.hasGeometry && worldAnchor != null,
            e.restLength,
            source,
            to.getGameTime() + 200));
  }

  static boolean restore(ServerLevel level, CouplerEndpoint e) {
    var key = GlobalPos.of(level.dimension(), e.getBlockPos());
    var transfer = TRANSFERS.get(key);
    if (transfer == null || !transfer.endpoint.equals(e.endpointId)) return true;
    var b = body(level, e);
    if (transfer.geometry && (b == null || b.getMassTracker().getMass() <= 0)) return false;
    if (!CouplerSavedData.get(level).move(e.endpointId, transfer.source.pos(), e.getBlockPos())) {
      TRANSFERS.remove(key);
      return true; // Registration will reject the stale handoff as a copied endpoint.
    }
    if (transfer.geometry) {
      e.geometry(
          local(b, transfer.anchor).sub(blockOrigin(e)),
          localFrame(b, transfer.frame),
          transfer.length);
    } else {
      e.hasGeometry = false;
      e.setChanged();
    }
    TRANSFERS.remove(key);
    MOVING.put(transfer.source, new Moving(e.endpointId, level.getGameTime() + 5));
    return true;
  }

  static void prune(ServerLevel level) {
    TRANSFERS
        .entrySet()
        .removeIf(
            e ->
                e.getKey().dimension().equals(level.dimension())
                    && e.getValue().expires < level.getGameTime());
    MOVING
        .entrySet()
        .removeIf(
            e ->
                e.getKey().dimension().equals(level.dimension())
                    && e.getValue().expires < level.getGameTime());
  }

  static void unload(ServerLevel level) {
    TRANSFERS
        .entrySet()
        .removeIf(
            e ->
                e.getKey().dimension().equals(level.dimension())
                    || e.getValue().source.dimension().equals(level.dimension()));
    MOVING.keySet().removeIf(p -> p.dimension().equals(level.dimension()));
  }

  private CouplerTransfers() {}
}
