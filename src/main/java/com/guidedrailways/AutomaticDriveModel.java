package com.guidedrailways;

/** Only produces throttle/brake requests; physics remains responsible for motion. */
public final class AutomaticDriveModel {
  public static final double STOP_TOLERANCE = .25, STOP_SPEED = .12;

  public record Demand(double ceiling, int notch) {}

  public static Demand demand(double distance, double speed, double available, double braking) {
    if (!Double.isFinite(distance)
        || !Double.isFinite(speed)
        || !Double.isFinite(available)
        || available <= 0
        || braking <= 0) return new Demand(0, -4);
    // Reserve reaction distance and use 55% of configured service braking for the profile.
    double room = Math.max(0, distance - .08 - Math.max(0, speed) * .15);
    double ceiling = Math.min(available, Math.sqrt(2 * braking * .55 * room));
    if (distance < 1) ceiling = Math.min(ceiling, .65);
    if (distance <= .08) return new Demand(0, -4);
    if (speed > ceiling + .06)
      return new Demand(
          ceiling,
          -Math.clamp((int) Math.ceil((speed - ceiling) * 4 / Math.max(.1, braking * .3)), 1, 4));
    return new Demand(ceiling, speed < ceiling - .04 ? DriverModel.MAX_NOTCH : 0);
  }

  public static boolean arrived(double distance, double maximumSpeed) {
    return Math.abs(distance) <= STOP_TOLERANCE && maximumSpeed <= STOP_SPEED;
  }
}
