package com.guidedrailways;

/** Closed-loop effort/drag/brake scenarios, without launching a server. */
public final class DriverModelCheck {
  private static int checks;

  static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  static double force(
      double speed,
      double limit,
      double rpm,
      double mass,
      int notch,
      boolean emergency,
      boolean hold,
      double dt) {
    return DriverModel.force(speed, limit, rpm, mass, notch, emergency, hold, 6, 2.5, 4, .6, dt);
  }

  static double run(
      double speed,
      double limit,
      double rpm,
      double mass,
      int notch,
      boolean emergency,
      boolean hold,
      double seconds,
      double dt) {
    for (int i = 0; i < Math.round(seconds / dt); i++) {
      double f = force(speed, limit, rpm, mass, notch, emergency, hold, dt),
          next = speed + f / mass * dt;
      check(Double.isFinite(next), "finite integration");
      check(Math.abs(next - speed) <= 8 * dt + 1e-9, "bounded acceleration or braking");
      if (emergency || hold || notch <= 0)
        check(speed * next >= -1e-10, "braking never reverses travel");
      speed = next;
    }
    return speed;
  }

  public static void main(String[] args) {
    // Regression: one powered bogey and two trailers previously used only 111.5 mass
    // for traction, while both trailers added .6 blocks/s^2 of coasting resistance.
    for (int sign : new int[] {-1, 1}) {
      double[] masses = {111.5, 111.5, 103}, rpm = {sign * 256, 0, 0};
      var shares = TractionBudget.shares(326, rpm);
      double total = 0;
      for (int i = 0; i < 3; i++) {
        double f =
            DriverModel.manualForce(
                sign * 4, sign * 25.6, rpm[i], masses[i], 8, false, false, 6, 2.5, 4, .6, .025,
                shares[i], true);
        check(
            Math.abs(f) <= Math.abs(rpm[i]) * 6 + 1e-8,
            "trailers gain no phantom motor and actual motor keeps RPM force cap");
        if (i > 0)
          check(f == 0, "an unpowered trailer freewheels during a consist traction command");
        total += f;
      }
      check(
          Math.abs(total / 326 - sign * 2.5) < 1e-8,
          "the configured acceleration ceiling applies to the complete consist rather than one motor's supported mass");
      check(
          Math.abs(
                  DriveModel.force(0, sign * 256, 111.5, false, .1, 6, 2.5, 4, shares[0]) / 326
                      - sign * 2.5)
              < 1e-8,
          "direct RPM drive shares the same whole-consist ceiling");
      for (int notch : new int[] {-4, -1, 0}) {
        double f =
            DriverModel.manualForce(
                sign * 4, sign * 25.6, 0, 103, notch, false, false, 6, 2.5, 4, .6, .025, 0, true);
        check(
            f * sign < 0, "freewheel exception never suppresses an actual brake or coasting notch");
      }
      check(
          DriverModel.force(
                      sign * 4, sign * 25.6, 0, 103, 8, true, false, 6, 2.5, 4, .6, .025, 0, true)
                  * sign
              < 0,
          "emergency braking remains effective on trailers");
      check(
          DriverModel.force(
                      sign * 4, sign * 25.6, 0, 103, 8, false, true, 6, 2.5, 4, .6, .025, 0, true)
                  * sign
              < 0,
          "station or power-loss hold remains effective on trailers");
    }
    for (double mass : new double[] {326, 1000, 10000})
      for (double input : new double[] {16, 64, 256}) {
        var shares = TractionBudget.shares(mass, new double[] {input, 0});
        double f =
            DriverModel.manualForce(
                0, 25.6, input, 100, 8, false, false, 6, 2.5, 4, .6, .025, shares[0], true);
        check(
            Math.abs(f - Math.min(input * 6, mass * 2.5)) < 1e-7,
            "heavy loads still slow acceleration when the real RPM force budget is exhausted");
      }
    var split = TractionBudget.shares(900, new double[] {256, -128, 0});
    check(
        split[0] == 600 && split[1] == 300 && split[2] == 0,
        "oppositely installed motors share acceleration mass in proportion to absolute input RPM");
    check(
        java.util.Arrays.stream(TractionBudget.shares(900, new double[] {0, 0})).sum() == 0,
        "disconnecting all inputs leaves no available traction");
    check(
        DriverModel.force(4, 25.6, 0, 100, 8, false, false, 6, 2.5, 4, .6, .025, 0, false) < 0,
        "all-power-loss coasting behavior is retained");
    for (double dt : new double[] {.05, 1d / 60, 1d / 120})
      for (double mass : new double[] {80, 600, 5000})
        for (int direction : new int[] {-1, 1}) {
          double limit = direction * 12.8;
          double low = run(0, limit, -256, mass, 1, false, false, 2, dt),
              high = run(0, limit, 256, mass, 8, false, false, 2, dt);
          check(
              Math.abs(high) > Math.abs(low) * 2,
              "notches change effort/acceleration, independent of RPM sign");
          double lowFinal = run(low, limit, 256, mass, 1, false, false, 480, dt),
              highFinal = run(high, limit, 256, mass, 8, false, false, 480, dt);
          check(
              Math.abs(lowFinal - limit) < .001 && Math.abs(highFinal - limit) < .001,
              "all positive notches share the RPM ceiling");
          check(
              force(direction * 6, limit, 256, mass, 1, false, false, dt) * direction > 0,
              "reducing to P1 does not brake toward one quarter speed");
          check(
              force(direction * 15, limit, 256, mass, 1, false, false, dt) == 0,
              "powered overspeed tapers without secret braking");
          double coast = run(direction * 6, limit, 256, mass, 0, false, false, 1, dt);
          check(
              Math.abs(coast) > 5 && Math.abs(coast) < 6,
              "centre coasts despite available stress input");
          double b1 = run(direction * 6, limit, 256, mass, -1, false, false, 1, dt),
              b4 = run(direction * 6, limit, 256, mass, -4, false, false, 1, dt),
              estop = run(direction * 6, limit, 0, mass, 4, true, false, 1, dt);
          check(
              Math.abs(estop) < Math.abs(b4) && Math.abs(b4) < Math.abs(b1),
              "four graded brakes and independent stronger emergency brake");
          check(
              Math.abs(run(direction * 6, limit, 0, mass, 4, false, true, 10, dt)) < 1e-10,
              "power-loss/authority-loss hold stops without RPM");
          check(
              force(0, 0, 256, mass, 4, false, false, dt) == 0,
              "neutral reverser cannot produce traction");
          check(force(0, limit, 0, mass, 4, false, false, dt) == 0, "no phantom power");
          check(
              Math.abs(run(direction * 6, limit, 256, mass, -1, false, false, 10, dt)) < 1e-10,
              "B1 eventually stops, with no speed inversion");
        }
    for (int n = -4; n <= 8; n++) {
      check(DriverModel.step(n, 1) == Math.min(8, n + 1), "one positive detent per step");
      check(DriverModel.step(n, -1) == Math.max(-4, n - 1), "one negative detent per step");
      check(DriverModel.canChangeDirection(0, n) == (n <= 0), "reverser requires no traction");
      check(!DriverModel.canChangeDirection(.151, n), "moving reverser interlock");
    }
    check(!DriverModel.canChangeDirection(Double.NaN, 0), "invalid speed cannot unlock reverser");
    check(force(Double.NaN, 4, 256, 80, 1, false, false, .05) == 0, "invalid state rejected");
    check(
        force(1, 4, 256, 80, -99, false, false, .05) == force(1, 4, 256, 80, -4, false, false, .05),
        "notch range clamped");
    check(
        DriverModel.lever(-4) == -1 && DriverModel.lever(0) == 0 && DriverModel.lever(8) == 1,
        "asymmetric brake and power ranges retain the full visual travel");
    System.out.println("DriverModelCheck: " + checks + " assertions passed");
  }
}
