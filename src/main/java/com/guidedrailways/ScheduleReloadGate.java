package com.guidedrailways;

import java.util.*;

/** Wait for the saved consist to settle before rebuilding navigation after load. */
public final class ScheduleReloadGate {
  public enum Result {
    WAIT,
    READY,
    CHANGED
  }

  private final Set<UUID> expected;
  private Set<UUID> previous = Set.of();
  private long deadline = Long.MIN_VALUE;
  private int stable;

  public ScheduleReloadGate(Set<UUID> expected) {
    this.expected = expected == null ? Set.of() : Set.copyOf(expected);
  }

  static boolean missing(Set<UUID> expected, Set<UUID> actual) {
    return actual.isEmpty() || expected != null && !actual.containsAll(expected);
  }

  public Result tick(Set<UUID> actual, long tick, boolean complete) {
    if (!complete || missing(expected, actual)) {
      deadline = Long.MIN_VALUE;
      stable = 0;
      previous = Set.of();
      return Result.WAIT;
    }
    if (deadline == Long.MIN_VALUE) deadline = tick + 200;
    if (!expected.isEmpty() && !expected.equals(actual)) {
      stable = 0;
      previous = Set.of();
      return tick >= deadline ? Result.CHANGED : Result.WAIT;
    }
    if (!previous.equals(actual)) {
      previous = Set.copyOf(actual);
      stable = 0;
    }
    return ++stable >= 10 ? Result.READY : Result.WAIT;
  }
}
