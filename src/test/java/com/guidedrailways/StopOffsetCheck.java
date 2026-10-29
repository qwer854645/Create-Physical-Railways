package com.guidedrailways;

/** Sign convention and tolerance band of the stop offset. */
public final class StopOffsetCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected stop offset: " + what);
  }

  public static void main(String[] args) {
    check(StopOffset.ahead(3) == 3, "a stop ahead is positive");
    check(StopOffset.ahead(-3) == 3, "the sign of the distance argument does not flip the meaning");
    check(StopOffset.past(3) == -3, "a stop behind is negative");
    check(StopOffset.past(-3) == -3, "the same for a passed stop");

    check(StopOffset.band(0) == StopOffset.Band.ON, "zero offset is on the stop");
    check(StopOffset.band(StopOffset.TOLERANCE) == StopOffset.Band.ON, "the band edge counts as on");
    check(StopOffset.band(-StopOffset.TOLERANCE) == StopOffset.Band.ON, "and on the other side");
    check(
        StopOffset.band(StopOffset.TOLERANCE + 1e-6) == StopOffset.Band.SHORT,
        "just outside the band is short");
    check(
        StopOffset.band(-StopOffset.TOLERANCE - 1e-6) == StopOffset.Band.OVER,
        "just outside the band is over");

    check(StopOffset.band(StopOffset.ahead(5)) == StopOffset.Band.SHORT, "5 blocks short reports short");
    check(StopOffset.band(StopOffset.past(5)) == StopOffset.Band.OVER, "5 blocks past reports over");

    check(!StopOffset.wrongSide(StopOffset.TOLERANCE), "the band itself is not a fault");
    check(!StopOffset.wrongSide(StopOffset.TOLERANCE * 2), "twice the band is still fine tuning");
    check(StopOffset.wrongSide(StopOffset.TOLERANCE * 3 + 1e-6), "beyond three bands is a fault");
    check(StopOffset.wrongSide(StopOffset.past(2)), "overshooting is a fault on the same scale");

    System.out.println("StopOffsetCheck: " + checks + " stop offset decisions passed");
  }
}
