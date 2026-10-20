package com.guidedrailways;

public final class ElectricDriveCheck {
  private static int checks;

  private static void near(double actual, double expected) {
    checks++;
    if (!Double.isFinite(actual)
        || Math.abs(actual - expected) > 1e-7 * Math.max(1, Math.abs(expected)))
      throw new AssertionError(actual + " != " + expected);
  }

  public static void main(String[] args) {
    near(ElectricDriveModel.resistance(3000, 40000), 225);
    near(ElectricDriveModel.resistance(Double.NaN, 40000), 225);
    near(ElectricDriveModel.resistiveDemand(3000, 225), 40000);
    near(ElectricDriveModel.resistiveDemand(1500, 225), 10000);
    near(ElectricDriveModel.resistiveDemand(-3000, 225), 40000);
    near(ElectricDriveModel.resistiveDemand(0, 225), 40000);
    near(ElectricDriveModel.resistiveDemand(3000, 0), .1);
    near(ElectricDriveModel.resistiveDemand(3000, Double.NaN), .1);
    near(ElectricDriveModel.delivered(3000, 10, 1000), 25500);
    near(ElectricDriveModel.delivered(-3000, -10, 1000), 25500);
    near(ElectricDriveModel.delivered(999, 100, 1000), 0);
    near(ElectricDriveModel.delivered(3000, 0, 1000), 0);
    near(ElectricDriveModel.delivered(Double.NaN, 10, 1000), 0);
    near(ElectricDriveModel.delivered(3000, Double.POSITIVE_INFINITY, 1000), 0);
    near(ElectricDriveModel.limit(1000, 20, 5000, 1), 250);
    near(ElectricDriveModel.limit(-1000, -20, 5000, -1), -250);
    near(ElectricDriveModel.limit(1000, 0, 5000, 1), 1000);
    near(ElectricDriveModel.limit(1000, 20, 0, 1), 0);
    near(ElectricDriveModel.limit(-1000, 20, 0, 1), -1000); // Braking survives wire loss.
    near(ElectricDriveModel.limit(1000, -20, 0, -1), 1000);
    var random = new java.util.Random(7241);
    for (int i = 0; i < 10000; i++) {
      double speed = .5 + random.nextDouble() * 100, watts = random.nextDouble() * 1e6;
      double force = random.nextDouble() * 1e6, dt = .005 + random.nextDouble() * .1;
      double output = ElectricDriveModel.limit(force, speed, watts, 1);
      if (output < 0 || output > force + 1e-8 || output * speed * dt > watts * dt + 1e-6)
        throw new AssertionError("mechanical work exceeded electrical budget");
      checks++;
    }
    System.out.println(
        "ElectricDriveCheck: "
            + checks
            + " load, brownout, reverse, braking and energy-budget checks passed");
  }
}
