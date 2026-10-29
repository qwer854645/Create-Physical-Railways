package com.guidedrailways;

/** What auto align asks for, and the three ways that request can end. */
public final class AlignDemandCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected align demand: " + what);
  }

  public static void main(String[] args) {
    // Unknown offset ends alignment instead of driving on a stale number.
    var unknown = AlignDemand.of(Double.NaN, 0, false);
    check(unknown.stop(), "an unknown offset stops alignment");
    check(!unknown.override(), "and leaves the lever untouched while doing so");

    // Inside the band: finished, and the lever is handed back.
    var arrived = AlignDemand.of(0, 0, false);
    check(arrived.stop(), "reaching the stop ends alignment");
    check(!arrived.override(), "and does not keep commanding the train");

    // The emergency brake ends it too.
    // Under the emergency brake alignment stops asking for movement but keeps holding the ceiling at zero, which is
    // what the command builder did before this decision was named.
    var braking = AlignDemand.of(2, 0, true);
    check(braking.cap() == 0 && braking.notch() == 0, "the emergency brake stops alignment asking for movement");
    check(!braking.stop(), "and does not finish it: the brake is the driver's to release");

    // Creeping forward and backward: the notch follows the offset, the ceiling is its size.
    var forward = AlignDemand.of(2, 0, false);
    check(forward.override(), "a live alignment overrides the lever");
    check(forward.cap() > 0 && forward.cap() <= AutoAlign.CREEP_SPEED, "ceiling stays inside creep");
    check(forward.notch() == 1, "a stop ahead asks for the forward notch");

    var backward = AlignDemand.of(-2, 0, false);
    check(backward.notch() == -1, "a stop behind asks for the reverse notch");
    check(backward.cap() > 0, "and a usable ceiling");

    // Too fast or too far: refused, and refusal pins the ceiling to zero rather than letting the lever drive.
    var fast = AlignDemand.of(2, AutoAlign.MAX_ENTRY_SPEED + 1, false);
    check(fast.override() && fast.cap() == 0, "a refused alignment does not let the lever drive");
    check(fast.notch() == 0, "and asks for the neutral notch");
    check(!fast.stop(), "a refusal is not a finish: the next tick may try again");

    var far = AlignDemand.of(AutoAlign.GIVE_UP + 1, 0, false);
    check(far.override() && far.cap() == 0, "an implausible offset is refused the same way");

    // The three outcomes are distinct, so a caller cannot confuse them.
    check(!forward.stop() && forward.override(), "creep is an override, not a stop");
    check(!arrived.override(), "a stop is not an override");

    System.out.println("AlignDemandCheck: " + checks + " align demands passed");
  }
}
