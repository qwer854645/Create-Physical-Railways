package com.physicalrailways;

public final class RecoveryWindowCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var failed = new RecoveryWindow();
    var unaffected = new RecoveryWindow();
    check(!failed.blocked() && failed.healthy(0), "normal trains have no startup delay");
    failed.failed(0);
    check(failed.blocked() && unaffected.healthy(0), "a fault does not block another train");
    for (int i = 0; i < 50; i++) failed.failed(0);
    check(!failed.probe(19) && failed.probe(20), "substeps neither extend nor escalate cooldown");
    for (int i = 0; i < 50; i++)
      check(!failed.healthy(20), "substeps do not count as stable ticks");
    for (int tick = 21; tick < 29; tick++)
      check(!failed.healthy(tick), "wait for ten consecutive ticks");
    check(failed.healthy(29) && !failed.blocked(), "full stable window releases the hold");
    failed.failed(30);
    check(!failed.probe(69) && failed.probe(70), "a repeated failure backs off for forty ticks");
    failed.healthy(70);
    failed.healthy(72);
    for (int tick = 73; tick < 81; tick++)
      check(!failed.healthy(tick), "a gap restarts the stable streak");
    check(failed.healthy(81), "a complete fresh streak recovers after a gap");
    failed.unavailable(90);
    check(!failed.probe(109) && failed.probe(110), "unloaded members get a bounded retry interval");
    failed.healthy(110);
    failed.unavailable(111);
    check(!failed.probe(130) && failed.probe(131), "a second unload discards partial readiness");
    check(!failed.probe(10) && failed.probe(30), "clock rollback rebases cooldown");
    var persistent = new RecoveryWindow();
    long now = 0;
    for (int delay : new int[] {20, 40, 80, 160, 200, 200}) {
      persistent.failed(now);
      check(!persistent.probe(now + delay - 1), "backoff cannot expire early");
      now += delay;
      check(persistent.probe(now), "backoff is capped and eventually probes again");
    }
    persistent.failed(now + 500);
    check(persistent.probe(now + 520), "a long quiet interval resets failure escalation");
    System.out.println(
        "RecoveryWindowCheck: "
            + checks
            + " fault isolation, backoff, stable ticks and rollback checks passed");
  }
}
