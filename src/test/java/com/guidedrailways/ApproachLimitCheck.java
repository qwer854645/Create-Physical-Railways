package com.guidedrailways;

/**
 * What a unit is allowed to believe about the room in front of it.
 *
 * <p>Each case here is one of the mistakes that was actually made in the physics step, so the numbers are
 * asserted rather than described.
 */
public final class ApproachLimitCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected approach limit: " + what);
  }

  private static void close(double actual, double expected, String what) {
    checks++;
    boolean ok =
        Double.isInfinite(expected)
            ? Double.isInfinite(actual) && Math.signum(actual) == Math.signum(expected)
            : Math.abs(actual - expected) < 1e-9;
    if (!ok) throw new AssertionError(what + ": expected " + expected + " but was " + actual);
  }

  public static void main(String[] args) {
    // Standing on a switch reports no room. Holding there is a deadlock, because the only way to clear a
    // switch is to move off it.
    close(ApproachLimit.switchRoom(0), Double.POSITIVE_INFINITY, "a reached switch imposes no limit");
    close(ApproachLimit.switchRoom(.05), Double.POSITIVE_INFINITY, "the creep band counts as reached");
    close(ApproachLimit.switchRoom(.09), .09, "just outside the band is a real approach");

    // A real approach must keep its distance, or the train cannot brake for it.
    close(ApproachLimit.switchRoom(20), 20, "a real approach keeps its room");
    close(ApproachLimit.switchRoom(Double.POSITIVE_INFINITY), Double.POSITIVE_INFINITY, "no switch ahead");

    // The combined form must keep the plan's smaller limit: clearing the switch distance must never clear a
    // platform stop, which is what made trains overrun their stations.
    close(ApproachLimit.combined(0, 5), 5, "a reached switch still leaves the platform stop");
    close(ApproachLimit.combined(0, Double.POSITIVE_INFINITY), Double.POSITIVE_INFINITY, "nothing stops it");
    close(ApproachLimit.combined(20, 5), 5, "a platform closer than a switch wins");
    close(ApproachLimit.combined(20, Double.POSITIVE_INFINITY), 20, "a switch closer than a platform wins");

    // The threshold itself is part of the contract: the physics step passes room through unchanged above it.
    check(ApproachLimit.CREEP > 0, "the creep band is positive");

    System.out.println("ApproachLimitCheck: " + checks + " approach limit decisions passed");
  }
}
