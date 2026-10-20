package com.guidedrailways;

public final class WheelAnimationCheck {
  private static int checks;

  private static void near(double actual, double expected) {
    checks++;
    if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1e-8)
      throw new AssertionError(actual + " != " + expected);
  }

  public static void main(String[] args) {
    for (double speed : new double[] {0, 1, 12, 25.6, -12, -25.6}) {
      var animation = new WheelAnimation();
      animation.accept(0, speed, 0);
      for (int tick = 0; tick < 1000; tick++) {
        if (tick % 2 == 0) animation.accept(speed * tick / 20, speed, tick);
        for (int frame = 0; frame < 8; frame++) {
          float partial = frame / 8f;
          near(animation.sample(tick, partial), speed * (tick + partial) / 20);
        }
      }
    }
    var animation = new WheelAnimation();
    animation.accept(0, 12, 0);
    double before = animation.sample(3, 0);
    animation.accept(1.2, 12, 3); // A packet was delayed by one client tick.
    near(animation.sample(3, 0), before);
    near(animation.sample(7, 0), 3.6); // Reconciles without snapping on receipt.
    before = animation.sample(7, 0);
    animation.accept(3.6, 0, 7);
    near(animation.sample(7, 0), before);
    near(animation.sample(100, .5f), 3.6);
    animation.accept(5000, -12, 101); // New placement or relocation.
    near(animation.sample(101, 0), 5000);
    animation.accept(20, 1, 0); // New world clock.
    near(animation.sample(0, 0), 20);
    animation.accept(Double.NaN, 1, 1);
    near(animation.sample(1, 0), 20.05);
    System.out.println(
        "WheelAnimationCheck: "
            + checks
            + " packet gap, tick boundary, reverse, stop and reset checks passed");
  }
}
