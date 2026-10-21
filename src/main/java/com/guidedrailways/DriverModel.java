package com.guidedrailways;

/**
 * Eight traction effort steps and four independent brake steps. Manual speed profiles are supplied
 * by the console.
 */
public final class DriverModel {
  public static final int MAX_NOTCH = 8, BRAKE_NOTCHES = 4;
  public static final double STOP_SPEED = .15;

  private DriverModel() {}

  public static boolean canChangeDirection(double speed, int notch) {
    return Double.isFinite(speed) && Math.abs(speed) <= STOP_SPEED && notch <= 0;
  }

  public static int step(int notch, int delta) {
    return Math.clamp(notch + Integer.signum(delta), -BRAKE_NOTCHES, MAX_NOTCH);
  }

  public static double lever(int notch) {
    return notch / (double) (notch < 0 ? BRAKE_NOTCHES : MAX_NOTCH);
  }

  public static double manualForce(
      double speed,
      double ceiling,
      double rpm,
      double mass,
      int notch,
      boolean emergency,
      boolean hold,
      double forcePerRpm,
      double acceleration,
      double braking,
      double coasting,
      double dt,
      double tractionMass,
      boolean trainPowered) {
    if (notch > 0
        && !emergency
        && !hold
        && Double.isFinite(speed)
        && Double.isFinite(ceiling)
        && Double.isFinite(rpm)
        && Math.abs(rpm) > .001
        && speed * Math.signum(ceiling) > Math.abs(ceiling) + .06)
      return DriveModel.coastForce(
          speed, mass, Math.min(braking, 2 * (Math.abs(speed) - Math.abs(ceiling))), dt);
    // A manual positive notch selects SPEED, not an effort percentage.
    return force(
        speed,
        ceiling,
        rpm,
        mass,
        notch > 0 ? MAX_NOTCH : notch,
        emergency,
        hold,
        forcePerRpm,
        acceleration,
        braking,
        coasting,
        dt,
        tractionMass,
        trainPowered);
  }

  public static double force(
      double speed,
      double ceiling,
      double rpm,
      double mass,
      int notch,
      boolean emergency,
      boolean hold,
      double forcePerRpm,
      double acceleration,
      double braking,
      double coasting,
      double dt,
      double tractionMass,
      boolean trainPowered) {
    if (!Double.isFinite(speed)
        || !Double.isFinite(ceiling)
        || !Double.isFinite(rpm)
        || !Double.isFinite(mass)
        || mass <= 0
        || dt <= 0
        || !Double.isFinite(dt)) return 0;
    notch = Math.clamp(notch, -BRAKE_NOTCHES, MAX_NOTCH);
    if (emergency) return DriveModel.coastForce(speed, mass, braking * 2, dt);
    if (hold) return DriveModel.coastForce(speed, mass, braking, dt);
    if (notch < 0)
      return DriveModel.coastForce(speed, mass, braking * (-notch / (double) BRAKE_NOTCHES), dt);
    if (notch == 0 || Math.abs(ceiling) < 1e-8 || Math.abs(rpm) < .001 && !trainPowered)
      return DriveModel.coastForce(speed, mass, coasting, dt);
    // A trailer freewheels under a powered traction command; it is not an extra coasting brake.
    if (Math.abs(rpm) < .001) return 0;
    // Defensive fallback; the reverser itself is interlocked against moving changes.
    if (ceiling * speed < 0 && Math.abs(speed) > STOP_SPEED)
      return DriveModel.coastForce(speed, mass, braking, dt);
    double error = (ceiling - speed) * Math.signum(ceiling);
    if (error <= 0) return 0; // Reducing power never silently applies the service brake.
    double effort = notch / (double) MAX_NOTCH;
    double limit = Math.min(Math.abs(rpm) * forcePerRpm, tractionMass * acceleration) * effort;
    return Math.copySign(
        Math.min(error * tractionMass * 2, Math.min(limit, tractionMass * error / dt)), ceiling);
  }
}
