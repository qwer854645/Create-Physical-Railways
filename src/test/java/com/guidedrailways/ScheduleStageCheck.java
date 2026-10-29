package com.guidedrailways;

/**
 * Every controller code maps to exactly one stage, and each stage answers the caller's questions the same way.
 *
 * <p>The list of codes is spelled out here rather than derived from the controller, so adding a code without deciding
 * what it means for callers fails this check instead of silently landing in the default branch.
 */
public final class ScheduleStageCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected schedule stage: " + what);
  }

  public static void main(String[] args) {
    // Every code the controller defines, with the stage it must report.
    record Case(int code, ScheduleStage expected, String what) {}
    var cases =
        java.util.List.of(
            new Case(ScheduleController.EMPTY, ScheduleStage.IDLE, "empty"),
            new Case(ScheduleController.FINISHED, ScheduleStage.COMPLETE, "finished"),
            new Case(ScheduleController.PLANNING, ScheduleStage.PLANNING, "planning"),
            new Case(ScheduleController.TRAVELLING, ScheduleStage.RUNNING, "travelling"),
            new Case(ScheduleController.WAITING, ScheduleStage.WAITING, "waiting at a stop"),
            new Case(ScheduleController.SIGNAL_WAIT, ScheduleStage.WAITING, "waiting at a signal"),
            new Case(ScheduleController.PAUSED, ScheduleStage.HOLDING, "paused"),
            new Case(ScheduleController.CHANGED, ScheduleStage.HOLDING, "schedule changed"),
            new Case(ScheduleController.OVERSHOOT, ScheduleStage.HOLDING, "overshoot correction"),
            new Case(ScheduleController.UNSUPPORTED, ScheduleStage.BLOCKED, "unsupported instruction"),
            new Case(ScheduleController.NO_ROUTE, ScheduleStage.BLOCKED, "no route"),
            new Case(ScheduleController.NO_POWER, ScheduleStage.BLOCKED, "no power"),
            new Case(ScheduleController.TRACK_MISSING, ScheduleStage.BLOCKED, "track missing"),
            new Case(ScheduleController.CONFLICT, ScheduleStage.BLOCKED, "conflict"),
            new Case(ScheduleController.UNLOADED, ScheduleStage.BLOCKED, "cars unloaded"),
            new Case(ScheduleController.EMERGENCY, ScheduleStage.BLOCKED, "emergency"));

    for (var c : cases) check(ScheduleStage.of(c.code()) == c.expected(), c.what() + " maps to its stage");

    // Only travelling moves; nothing else may claim to.
    long moving = cases.stream().filter(c -> ScheduleStage.of(c.code()).moves()).count();
    check(moving == 1, "exactly one code lets the train move under its schedule, found " + moving);
    check(ScheduleStage.of(ScheduleController.TRAVELLING).moves(), "travelling moves");

    // A stop the player must fix is never reported as benign, and the reverse.
    for (var c : cases) {
      var stage = ScheduleStage.of(c.code());
      check(
          !(stage.benign() && stage.blocked()),
          c.what() + " cannot be both benign and blocked");
    }
    check(ScheduleStage.of(ScheduleController.NO_ROUTE).blocked(), "no route is the player's to fix");
    check(!ScheduleStage.of(ScheduleController.NO_ROUTE).benign(), "and is not benign");
    check(ScheduleStage.of(ScheduleController.SIGNAL_WAIT).benign(), "a signal wait resolves itself");
    check(ScheduleStage.of(ScheduleController.FINISHED).benign(), "a finished schedule is not a fault");

    // An unknown code must not be mistaken for a healthy one.
    check(ScheduleStage.of(15) == ScheduleStage.BLOCKED, "an unused code is reported as blocked");
    check(ScheduleStage.of(999) == ScheduleStage.BLOCKED, "an unknown code is reported as blocked");
    check(!ScheduleStage.of(999).moves(), "and never lets the train move");

    System.out.println("ScheduleStageCheck: " + checks + " schedule stages passed");
  }
}
