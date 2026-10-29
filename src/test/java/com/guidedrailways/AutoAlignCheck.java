package com.guidedrailways;

/** When auto align may drive a train, and how fast. */
public final class AutoAlignCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected auto align decision: " + what);
  }

  public static void main(String[] args) {
    // Inside the band: done, and not driving.
    var on = AutoAlign.step(0, 0, true, false);
    check(!on.active() && on.finished(), "standing on the stop finishes alignment");
    check(
        AutoAlign.step(StopOffset.TOLERANCE, 0, true, false).finished(),
        "the band edge counts as arrived");
    check(
        !AutoAlign.step(StopOffset.TOLERANCE + 1e-6, 0, true, false).finished(),
        "just outside the band is not finished");

    // The emergency brake always wins, and alignment does not resume by itself.
    var emergency = AutoAlign.step(2, 0, true, true);
    check(!emergency.active() && !emergency.finished(), "the emergency brake stops alignment");

    // Refuse when the offset cannot be trusted.
    check(
        !AutoAlign.step(AutoAlign.GIVE_UP + 1e-6, 0, true, false).active(),
        "an implausible offset refuses instead of driving blind");
    check(
        AutoAlign.step(AutoAlign.GIVE_UP - 1e-6, 0, true, false).active(),
        "an offset inside the limit is aligned");

    // Refuse until asked.
    check(!AutoAlign.step(2, 0, false, false).active(), "alignment does not start unless requested");

    // Refuse when the train is already too fast for a creep controller.
    check(
        !AutoAlign.step(2, AutoAlign.MAX_ENTRY_SPEED + 1e-6, true, false).active(),
        "a fast train must slow down before alignment takes over");
    check(
        AutoAlign.step(2, AutoAlign.MAX_ENTRY_SPEED, true, false).active(),
        "at the limit alignment may take over");

    // Creep is bounded from above, and it slows down as the stop gets closer.
    var far = AutoAlign.step(2, 0, true, false);
    check(far.active(), "a stop two blocks away is aligned");
    check(
        Math.abs(far.targetSpeed()) <= AutoAlign.CREEP_SPEED + 1e-9,
        "creep never exceeds the creep speed");
    var near = AutoAlign.step(.5, 0, true, false);
    check(
        Math.abs(near.targetSpeed()) < Math.abs(far.targetSpeed()),
        "the last half block is approached more slowly than two blocks");
    check(
        Math.abs(near.targetSpeed()) >= AutoAlign.MIN_SPEED - 1e-9,
        "creep never drops below the minimum and stalls");
    check(
        Math.abs(AutoAlign.step(StopOffset.TOLERANCE + 1e-9, 0, true, false).targetSpeed())
            >= AutoAlign.MIN_SPEED - 1e-9,
        "just outside the band still asks for a real speed rather than stalling");

    // The sign follows the offset, so a train short of the stop creeps backwards when it is past it.
    check(far.targetSpeed() > 0, "a stop ahead asks for positive speed");
    check(
        AutoAlign.step(-2, 0, true, false).targetSpeed() < 0,
        "a stop behind asks for negative speed");

    // Inactive commands never ask for thrust.
    check(far.finished() == false, "an active alignment is not finished");
    check(AutoAlign.step(2, 0, false, false).targetSpeed() == 0, "a refused command asks for nothing");

    System.out.println("AutoAlignCheck: " + checks + " auto align decisions passed");
  }
}
