package com.guidedrailways;

public final class SpeedSettingCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    for (double ratio : new double[] {.001, .05, .1, 1})
      check(
          Math.abs(SpeedSetting.fromRatio(ratio) / 10.0 - ratio * 256) <= .05,
          "configured RPM ratio maps to the gear editor's tenth precision");
    check(
        SpeedSetting.fromRatio(.00001) == SpeedSetting.MIN,
        "small positive limits clamp to the editor minimum");
    check(
        SpeedSetting.fromRatio(100) == SpeedSetting.MAX,
        "large limits clamp to the editor maximum");
    for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
      check(SpeedSetting.fromRatio(invalid) == 128, "invalid ratios use the editor fallback");
    for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0, 2561, Integer.MAX_VALUE})
      check(!SpeedSetting.valid(invalid), "out-of-range gear settings are rejected");
    int[] gears = {20, 40, 80, 100, 128, 160, 200, 256};
    check(
        SpeedSetting.validGears(SpeedSetting.MAX, gears),
        "eight ascending absolute target speeds are accepted");
    for (double available : new double[] {0, 3, 6.4, 12.8, 25.6})
      for (int notch = 1; notch <= 8; notch++) {
        check(
            SpeedSetting.gearCeiling(available, notch, gears)
                == Math.min(available, gears[notch - 1] / 10.0),
            "manual gear is an absolute speed capped only by available RPM");
      }
    check(
        SpeedSetting.gearCeiling(25.6, 3, gears) == 8
            && SpeedSetting.gearCeiling(12.8, 3, gears) == 8,
        "P3 remains 8 blocks/s at both 256 and 128 RPM, never 4 blocks/s");
    check(
        SpeedSetting.gearCeiling(6.4, 3, gears) == 6.4,
        "64 RPM cannot exceed its 6.4 blocks/s power ceiling");
    check(
        !SpeedSetting.validGears(SpeedSetting.MAX, new int[] {20, 40, 80}),
        "truncated network or saved gear lists are invalid");
    int[] descending = gears.clone();
    descending[4] = 1;
    check(
        !SpeedSetting.validGears(SpeedSetting.MAX, descending),
        "higher notches cannot request lower target speeds");
    for (int max : new int[] {1, 128, 256, 2560})
      check(
          SpeedSetting.validGears(max, SpeedSetting.defaultGears(max))
              && SpeedSetting.defaultGears(max)[7] == max,
          "default profiles retain the configured cap even at very low maxima");
    for (int direction : new int[] {-1, 1})
      for (double dt : new double[] {.05, 1d / 120}) {
        double speed = direction * 20;
        for (int i = 0; i < 20 / dt; i++) {
          double force =
              DriverModel.manualForce(
                  speed, direction * 8, 256, 600, 3, false, false, 6, 2.5, 4, .6, dt, 600, false);
          double next = speed + force / 600 * dt;
          check(
              Math.abs(next - speed) <= 4 * dt + 1e-9 && next * direction > 0,
              "manual downshift brakes within service limits without reversing");
          speed = next;
        }
        check(
            Math.abs(Math.abs(speed) - 8) < .061,
            "manual downshift converges to its configured absolute speed");
        double f1 =
            DriverModel.manualForce(0, 8, 256, 600, 1, false, false, 6, 2.5, 4, .6, dt, 600, false);
        double f8 =
            DriverModel.manualForce(0, 8, 256, 600, 8, false, false, 6, 2.5, 4, .6, dt, 600, false);
        check(f1 == f8, "manual notch denotes speed rather than an effort percentage");
        check(
            DriverModel.manualForce(0, 8, 0, 600, 3, false, false, 6, 2.5, 4, .6, dt, 600, false)
                == 0,
            "a configured gear cannot supply power to an unpowered bogie");
      }
    System.out.println("SpeedSettingCheck: " + checks + " assertions passed");
  }
}
