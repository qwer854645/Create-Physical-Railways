package com.guidedrailways;

/** The seam window, driven with the parameter values the running game actually produced. */
public final class SeamRulesCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(
        SeamRules.canAdvance(.95, 1, 999),
        "a sample inside the parameter window advances regardless of block distance");
    check(
        SeamRules.canAdvance(.5, 1, 0.04),
        "a sample within 0.6 blocks of the end advances regardless of parameter");
    check(
        !SeamRules.canAdvance(.5, 1, 999),
        "a sample far from the end in both senses is refused, which is the gate working");

    // Historical releases: the surviving axle sat at these parameters on a long edge, so the block
    // distance to the end was large while the parameter looked near. Edge length 20 blocks.
    for (double t : new double[] {.76, .8, .86, .9054, .9199}) {
      double blocksToEnd = (1 - t) * 20;
      boolean advances = SeamRules.canAdvance(t, 1, blocksToEnd * blocksToEnd);
      if (t >= 1 - SeamRules.WINDOW) {
        check(advances, "sample at " + t + " is inside the window and may advance");
      } else {
        check(
            !advances,
            "sample at " + t + " is outside both windows, so advancing is refused: the recorded"
                + " 37 seam releases are this side of the gate");
      }
    }

    // Reversed travel uses the other end of the edge.
    check(SeamRules.canAdvance(.05, 0, 999), "reversed travel advances at parameter 0");
    check(
        !SeamRules.canAdvance(.95, 0, 999),
        "reversed travel does not advance at the far end of the edge");

    // Boundary is inclusive on both tests, so a sample exactly on the window still advances.
    check(
        SeamRules.canAdvance(1 - SeamRules.WINDOW, 1, 999),
        "the parameter window is inclusive");
    check(
        SeamRules.canAdvance(.5, 1, SeamRules.NEAR_SQUARED),
        "the block window is inclusive");
    System.out.println("SeamRulesCheck: " + checks + " seam gate checks passed");
  }
}
