package com.guidedrailways;

/**
 * What a console is currently trying to do.
 *
 * <p>The console used to express this with overlapping flags - {@code active}, {@code observing}, {@code enabling}
 * through {@code enabled}, {@code aligning} and the schedule's own state - so two of them could be true at once and
 * the code that read them had to know which one won. That is the same shape as the junction memory that had to be
 * rewritten: a set of booleans standing in for one value, with the legal combinations left implicit.
 *
 * <p>One intent at a time, and the derivation is asserted so it cannot silently become two.
 */
enum ConsoleIntent {
  /** No plate is installed or none of its bodies resolve: the console drives nothing. */
  IDLE,
  /** A driver is at the console and the train follows the lever. */
  MANUAL,
  /** Auto align is creeping the train to its stop under the driver's instruction. */
  ALIGN,
  /** A schedule is running and the console is reporting it rather than driving. */
  SCHEDULE,
  /** A schedule exists but the driver has taken the lever. */
  OVERRIDE;

  /**
   * The intent, from the flags that exist.
   *
   * <p>Priority, highest first: aligning outranks driving by hand, because it only runs when the driver asked for
   * it and it is the one that must release itself. A schedule that is running outranks the lever, because the lever
   * is only live while the schedule is not observing.
   */
  static ConsoleIntent of(
      boolean bound, boolean aligning, boolean scheduleRunning, boolean scheduleObserving, boolean active) {
    if (!bound) return IDLE;
    if (aligning) return ALIGN;
    if (scheduleRunning && scheduleObserving) return SCHEDULE;
    if (scheduleRunning) return OVERRIDE;
    return active ? MANUAL : IDLE;
  }

  /** True when the lever may move the train. */
  boolean drives() {
    return this == MANUAL || this == ALIGN || this == OVERRIDE;
  }

  /** True when a schedule is expected to produce commands for this console. */
  boolean scheduled() {
    return this == SCHEDULE;
  }
}
