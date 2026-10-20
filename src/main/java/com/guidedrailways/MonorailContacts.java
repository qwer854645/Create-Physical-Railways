package com.guidedrailways;

import com.simibubi.create.content.trains.track.ITrackBlock;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

/** The native monorail's solid entity collider must not fight an attached bogie's rail guides. */
public final class MonorailContacts implements BlockSubLevelCollisionCallback {
  public static final MonorailContacts INSTANCE = new MonorailContacts();
  private static final CollisionResult GUIDED = new CollisionResult(new Vector3d(), true);

  private MonorailContacts() {}

  @Override
  public CollisionResult sable$onCollision(
      BlockPos rail, BlockPos other, Vector3d normal, double force) {
    var system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
    if (system == null || other == null) return CollisionResult.NONE;
    var level = system.getLevel();
    if (!level.hasChunkAt(rail) || !level.hasChunkAt(other)) return CollisionResult.NONE;
    var state = level.getBlockState(rail);
    if (!(state.getBlock() instanceof ITrackBlock track)
        || !RailGauge.MONORAIL.accepts(track.getMaterial().trackType.id.toString()))
      return CollisionResult.NONE;
    // Only the world railway is guided; loose rails on another body remain ordinary obstacles.
    if (SableCompanion.INSTANCE.getContaining(level, rail) != null) return CollisionResult.NONE;
    var part = level.getBlockState(other);
    RailGauge gauge;
    if (part.getBlock() instanceof BogieBlock) gauge = BogieBlock.gauge(part);
    else if (part.getBlock() instanceof CollisionPartBlock)
      gauge = BogieCollision.shape(part.getValue(CollisionPartBlock.SHAPE)).variant().gauge;
    else return CollisionResult.NONE;
    if (gauge != RailGauge.MONORAIL
        || !(SableCompanion.INSTANCE.getContaining(level, other) instanceof ServerSubLevel body))
      return CollisionResult.NONE;
    var unit = VehiclePhysics.unit(level, body.getUniqueId());
    if (unit == null || unit.detached || unit.lastSeen + 2 < level.getGameTime())
      return CollisionResult.NONE;
    return GUIDED;
  }
}
