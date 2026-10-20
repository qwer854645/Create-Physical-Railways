package com.guidedrailways;

/** Electrical load and bounded mechanical output; voltage alone never creates traction power. */
public final class ElectricDriveModel {
  private ElectricDriveModel() {}

  public static double resistance(double voltage, double watts) {
    double v = Double.isFinite(voltage) && Math.abs(voltage) >= 1 ? Math.abs(voltage) : 3000;
    double p = Double.isFinite(watts) ? Math.max(.1, watts) : .1;
    return Math.clamp(v * v / p, .001, 1e12);
  }

  public static double delivered(double voltage, double current, double minimumVoltage) {
    if (!Double.isFinite(voltage)
        || !Double.isFinite(current)
        || Math.abs(voltage) < minimumVoltage) return 0;
    double watts = Math.abs(voltage * current) * .85;
    return Double.isFinite(watts) ? watts : 0;
  }

  public static double limit(double force, double speed, double watts, double direction) {
    if (force * direction <= 0) return force;
    double available = Double.isFinite(watts) ? Math.max(0, watts) : 0;
    return Math.copySign(
        Math.min(Math.abs(force), available / Math.max(.5, Math.abs(speed))), force);
  }
}
