package com.guidedrailways;

/**
 * Which stage changes are worth looking at.
 *
 * <p>The controller has one writer for its code ({@code state(int)}), which is the seam this needs, but the legal
 * transitions were never written down. Deriving them from the code alone would guess, and guessing wrong here would
 * change behaviour, so this class starts the other way round: it names the handful of changes that cannot be right,
 * and everything else is allowed. A change it flags is logged, never blocked.
 *
 * <p>Once the log has shown which transitions actually happen, the rest of the table can be filled in from evidence
 * instead of from reading the call sites. That is why the default is permissive.
 */
final class ScheduleTransitions {
  /**
   * True when this change is one of the pairs that cannot be right.
   *
   * <p>Deliberately short: a train cannot start travelling from a state that means "no schedule" or "already
   * finished", and a schedule cannot finish before it has been planned.
   */
  static boolean unexpected(int from, int to) {
    if (from == to) return false;
    var fromStage = ScheduleStage.of(from);
    var toStage = ScheduleStage.of(to);
    if (toStage == ScheduleStage.RUNNING)
      return fromStage == ScheduleStage.IDLE || fromStage == ScheduleStage.COMPLETE;
    if (toStage == ScheduleStage.COMPLETE) return fromStage == ScheduleStage.IDLE;
    return false;
  }

  private ScheduleTransitions() {}
}
