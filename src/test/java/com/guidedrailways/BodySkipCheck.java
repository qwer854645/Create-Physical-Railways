package com.guidedrailways;

import java.util.UUID;

/**
 * The body-skip policy behind reading physics poses: which bodies are skipped, when a body is probed
 * again, and how many failures may reach the log.
 */
public final class BodySkipCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();

    var skip = new BodySkip();
    skip.begin(100);
    check(!skip.gone(first), "a body is readable until it fails");
    check(skip.mark(first), "marking an unreadable body is a new failure");
    check(skip.gone(first), "a marked body is skipped");
    check(!skip.mark(first), "marking the same body twice is not a new failure");
    check(skip.failures() == 2, "both reads count as failures");

    skip.begin(100);
    check(skip.gone(first), "the same tick keeps skipping the same body");

    skip.begin(101);
    check(!skip.gone(first), "a new tick probes the body again, because it may be repaired");

    var reporting = new BodySkip();
    reporting.begin(0);
    check(!reporting.shouldReport(), "nothing is reported before the first failure");
    for (int i = 0; i < 25; i++) {
      var body = UUID.randomUUID();
      reporting.mark(body);
      boolean expected = reporting.failures() == 1 || reporting.failures() % 20 == 0;
      check(
          reporting.shouldReport() == expected,
          "failure " + reporting.failures() + " must report exactly when the policy says");
    }
    check(reporting.failures() == 25, "every unreadable read is counted");

    // The physics step probes from three call sites and runs several substeps per tick, so a body
    // that read fine once must not be probed again inside that tick.
    var known = new BodySkip();
    known.begin(10);
    check(!known.known(first), "nothing is known before the first probe");
    known.markKnown(first);
    check(known.known(first), "a successful probe is remembered");
    known.begin(11);
    check(!known.known(first), "the next tick probes again");

    var cleared = new BodySkip();
    cleared.begin(7);
    cleared.mark(second);
    check(cleared.gone(second), "the body is skipped inside its tick");
    cleared.begin(8);
    check(!cleared.gone(second), "a later tick clears the skip set");
    check(cleared.failures() == 1, "clearing the set keeps the failure count for throttling");

    System.out.println("BodySkipCheck: " + checks + " body-skip policy checks passed");
  }
}
