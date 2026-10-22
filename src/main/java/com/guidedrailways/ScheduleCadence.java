package com.guidedrailways;

/** World-independent planning backoff and arrival debounce used by the schedule runtime. */
final class ScheduleCadence {
  private long nextPlan;
  private int stable;

  boolean beginPlanning(long tick) {
    if (tick < nextPlan) return false;
    nextPlan = tick + 10;
    return true;
  }

  boolean arrivalStable() {
    return ++stable >= 6;
  }

  void travelling() {
    stable = 0;
  }

  void restartPlanning() {
    nextPlan = 0;
  }

  void reset() {
    travelling();
    restartPlanning();
  }
}
