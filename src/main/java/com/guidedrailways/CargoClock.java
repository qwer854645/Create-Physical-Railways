package com.guidedrailways;

/** Successful transfers reset idle time even if the final inventory is unchanged. */
public final class CargoClock {
  private long last = Long.MIN_VALUE, version;

  public void exchanged(long tick, boolean simulation, long amount) {
    if (!simulation && amount > 0) {
      last = tick;
      version++;
    }
  }

  public long last() {
    return last;
  }

  public long version() {
    return version;
  }

  public boolean idle(long tick, long since, long duration) {
    return tick - Math.max(since, last) > Math.max(0, duration);
  }

  public static long itemAmount(int count, int max, boolean stacks) {
    return stacks ? (count > 0 && count == max ? 1 : 0) : Math.max(0, count);
  }

  public static boolean compare(long amount, long threshold, String operator) {
    return switch (operator) {
      case "GREATER" -> amount > threshold;
      case "LESS" -> amount < threshold;
      case "EQUAL" -> amount == threshold;
      default -> false;
    };
  }
}
