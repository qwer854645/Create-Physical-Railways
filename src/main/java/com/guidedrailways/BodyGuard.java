package com.guidedrailways;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drops bodies whose physics handle is already gone before a physics step reads their pose.
 *
 * <p>Sable's pipeline throws {@code RuntimeException("Body has been removed")} from {@code readPose}.
 * That exception used to reach the server tick and kill the whole world: the recorded crash reports
 * of 2026-10-19 and 2026-10-20 all end in {@code RapierPhysicsPipeline.assertBodyValid} called from
 * this project's step. {@code isRemoved()} only covers the Minecraft side, so a carriage torn down
 * inside the same substep can still hand us a stale physics handle.
 */
final class BodyGuard {
  private static final Logger LOG = LoggerFactory.getLogger(GuidedRailways.ID);

  private static final BodySkip SKIP = new BodySkip();

  static void beginStep(long tick) {
    SKIP.begin(tick);
  }

  /** True when the body can still be read this tick; unreadable bodies are remembered until the next. */
  static boolean readable(SubLevelPhysicsSystem physics, ServerSubLevel body) {
    if (body == null || body.isRemoved()) return false;
    var id = body.getUniqueId();
    if (SKIP.gone(id)) return false;
    // The physics step calls this from three places and runs several substeps per tick, so a body that
    // already read fine this tick must not be probed again: the probe allocates and crosses into native
    // code, and nothing can have changed since.
    if (SKIP.known(id)) return true;
    try {
      physics.getPipeline().readPose(body, new Pose3d());
      SKIP.markKnown(id);
      return true;
    } catch (RuntimeException removed) {
      SKIP.mark(id);
      if (SKIP.shouldReport())
        LOG.warn(
            "[BodyGone] {} unreadable bod(y/ies) this tick, skipping id={} : {}",
            SKIP.failures(),
            id,
            removed.toString());
      return false;
    }
  }

  /** Both the bogie and the carrier are read during a step, so both have to be alive. */
  static boolean readable(SubLevelPhysicsSystem physics, VehiclePhysics.Unit unit) {
    return readable(physics, unit.bogie) && readable(physics, unit.carrier);
  }

  private BodyGuard() {}
}
