package com.guidedrailways;

/**
 * Independent longitudinal controller. Unpowered coasting resistance and the service brake are
 * independent.
 */
public final class DriveModel {
  /** Opposes unpowered travel, capped by this step's momentum to avoid reversing at rest. */
  public static double coastForce(double speed, double mass, double deceleration, double dt) {
    if (!Double.isFinite(speed)
        || !Double.isFinite(mass)
        || !Double.isFinite(deceleration)
        || !Double.isFinite(dt)
        || mass <= 0
        || deceleration <= 0
        || dt <= 0) return 0;
    return -Math.signum(speed) * mass * Math.min(deceleration, Math.abs(speed) / dt);
  }

  public static double force(
      double speed,
      double rpm,
      double mass,
      boolean brake,
      double speedRatio,
      double forceRatio,
      double acceleration,
      double braking,
      double tractionMass) {
    if (!Double.isFinite(speed) || !Double.isFinite(rpm) || !Double.isFinite(mass) || mass <= 0)
      return 0;
    if (brake) return -Math.clamp(speed * 4, -braking, braking) * mass;
    if (Math.abs(rpm) < .001) return 0;
    double target = rpm * speedRatio;
    double limit = Math.min(Math.abs(rpm) * forceRatio, tractionMass * acceleration);
    // A powered drive tapers toward target; it does not brake a faster coasting vehicle.
    double error = target - speed;
    if (error * target <= 0) return 0;
    return Math.copySign(Math.min(Math.abs(error) * tractionMass * 2, limit), error);
  }
}
