package com.guidedrailways;

public final class ArrivalEstimateCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static ArrivalEstimate running(Object plan) {
    var estimate = new ArrivalEstimate();
    check(estimate.sample(plan, 0, 100, 10, true) == -1, "first observation cannot predict");
    for (int tick = 5; tick <= 20; tick += 5) {
      int predicted = estimate.sample(plan, tick, 100 - tick * .5, 10, true);
      check(
          tick < 20 ? predicted == -1 : predicted == 180,
          "one second warm-up and ticks/second conversion");
    }
    return estimate;
  }

  public static void main(String[] args) {
    Object plan = new Object();
    var estimate = running(plan);
    check(estimate.sample(plan, 25, 87.5, 10, true) == 175, "steady progress reduces estimate");
    check(
        estimate.sample(plan, 30, 87.5, 0, true) == -1,
        "stopped train loses its countdown immediately");
    check(estimate.sample(plan, 35, 85, 10, true) == -1, "restart must observe fresh progress");
    estimate = running(plan);
    check(
        estimate.sample(plan, 25, 87.5, 10, false) == -1,
        "signal, dock and power waits are indeterminate");
    check(estimate.sample(plan, 30, 85, 10, true) == -1, "resuming cannot reuse pre-wait speed");
    estimate = running(plan);
    check(
        estimate.sample(new Object(), 25, 20, 10, true) == -1,
        "replan or next stop resets history");
    estimate = running(plan);
    check(estimate.sample(plan, 25, 0, 10, true) == -1, "teleport is not high-speed travel");
    estimate = running(plan);
    check(estimate.sample(plan, 25, 91, 10, true) == -1, "moving away is not approaching");
    estimate = running(plan);
    check(
        estimate.sample(plan, 25, 90, 10, true) == -1,
        "wheel spin without route progress cannot predict");
    estimate = running(plan);
    check(
        estimate.sample(plan, 100, 50, 10, true) == -1,
        "unloaded interval does not imply uninterrupted running");
    estimate = running(plan);
    check(estimate.sample(plan, 10, 90, 10, true) == -1, "clock rollback resets observation");
    estimate = running(plan);
    estimate.reset();
    check(estimate.sample(plan, 25, 87.5, 10, true) == -1, "removed controller releases estimate");
    estimate = running(plan);
    int slowing = estimate.sample(plan, 25, 89, 4, true);
    check(
        slowing > 178 && slowing < 445,
        "braking updates smoothly within old and new speed estimates");
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
      estimate = running(plan);
      check(estimate.sample(plan, 25, invalid, 10, true) == -1, "invalid distance is unknown");
      check(estimate.sample(plan, 30, 85, invalid, true) == -1, "invalid speed is unknown");
    }
    // ETA is never the native arrived sentinel, even when distance rounds to zero.
    estimate = new ArrivalEstimate();
    for (int tick = 0; tick <= 40; tick += 5) {
      int predicted = estimate.sample(plan, tick, Math.max(0, 20 - tick * .5), 10, true);
      check(predicted != 0, "only confirmed station presence may publish zero");
    }
    System.out.println(
        "ArrivalEstimateCheck: " + checks + " progress, interruption and time-unit checks passed");
  }
}
