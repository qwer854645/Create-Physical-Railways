package com.guidedrailways;

/**
 * The transition table is permissive on purpose, and these assertions pin both halves of that.
 *
 * <p>A check that claimed to know every legal transition would be guessing, and enforcing that guess would change
 * behaviour. So the table only names pairs that cannot be right; everything else must pass.
 */
public final class ScheduleTransitionsCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected schedule transition: " + what);
  }

  public static void main(String[] args) {
    // A train cannot start travelling out of "no schedule" or "already finished".
    check(
        ScheduleTransitions.unexpected(ScheduleController.EMPTY, ScheduleController.TRAVELLING),
        "travelling straight out of empty is unexpected");
    check(
        ScheduleTransitions.unexpected(ScheduleController.FINISHED, ScheduleController.TRAVELLING),
        "travelling again after finishing without a new plan is unexpected");

    // Every ordinary path into travelling is allowed.
    for (int from :
        new int[] {
          ScheduleController.PLANNING,
          ScheduleController.WAITING,
          ScheduleController.SIGNAL_WAIT,
          ScheduleController.PAUSED,
          ScheduleController.OVERSHOOT,
          ScheduleController.CHANGED,
          ScheduleController.CONFLICT,
          ScheduleController.NO_ROUTE
        })
      check(
          !ScheduleTransitions.unexpected(from, ScheduleController.TRAVELLING),
          "resuming into travelling from code " + from + " must stay allowed");

    // Finishing needs a plan first.
    check(
        ScheduleTransitions.unexpected(ScheduleController.EMPTY, ScheduleController.FINISHED),
        "finishing straight out of empty is unexpected");
    check(
        !ScheduleTransitions.unexpected(ScheduleController.TRAVELLING, ScheduleController.FINISHED),
        "finishing from travelling is ordinary");

    // A state refreshing itself is never unexpected: the controller writes the same state on purpose.
    for (int code :
        new int[] {
          ScheduleController.EMPTY,
          ScheduleController.FINISHED,
          ScheduleController.TRAVELLING,
          ScheduleController.WAITING
        })
      check(
          !ScheduleTransitions.unexpected(code, code),
          "a state refreshing itself must never be flagged (code " + code + ")");

    // Anything else passes: the table must not block a change it has no evidence about.
    check(
        !ScheduleTransitions.unexpected(ScheduleController.PAUSED, ScheduleController.EMERGENCY),
        "an unlisted pair must pass");
    check(
        !ScheduleTransitions.unexpected(ScheduleController.NO_POWER, ScheduleController.WAITING),
        "an unlisted pair must pass in any direction");
    check(
        !ScheduleTransitions.unexpected(999, 998),
        "unknown codes must not be flagged for their own sake");

    System.out.println("ScheduleTransitionsCheck: " + checks + " transition rules passed");
  }
}
