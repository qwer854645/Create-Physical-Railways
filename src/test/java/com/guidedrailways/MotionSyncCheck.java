package com.guidedrailways;

/**
 * Pacing of the motion payloads. Each case is a decision that costs a client-side visual rebuild, so the
 * numbers are asserted rather than described: changing the pace has to be an explicit edit here.
 */
public final class MotionSyncCheck {
  private static int checks;

  private static void expect(boolean actual, boolean expected, String what) {
    checks++;
    if (actual != expected) throw new AssertionError("unexpected motion sync decision: " + what);
  }

  public static void main(String[] args) {
    var sync = new MotionSync();

    expect(sync.update(0, 0, 0), true, "the first payload is always sent");
    for (int tick = 1; tick < 100; tick++)
      expect(sync.update(tick, 0, 0), false, "an idle carriage is silent between heartbeats");
    expect(sync.update(100, 0, 0), true, "an idle carriage still sends a slow heartbeat");

    // Decelerating into a station used to send every two ticks. It now waits eight, because the client
    // interpolates the wheel animation from the last speed it was given.
    expect(sync.update(101, 4, .2), false, "a change alone does not send before the interval");
    expect(sync.update(104, 4, .4), false, "still inside the moving interval");
    expect(sync.update(108, 4, .6), true, "eight ticks after the last payload the change goes out");
    expect(sync.update(109, 4, .7), false, "the interval restarts from the payload just sent");
    expect(sync.update(116, 4, .8), true, "the next window closes on the following change");

    // A tiny change is not worth a rebuild: the wheel animation cannot show it.
    expect(sync.update(124, 4.01, .81), false, "a sub-epsilon change is ignored");
    expect(sync.update(132, 4.02, .82), false, "an ignored change is still below the threshold");

    expect(sync.update(140, 0, .82), true, "a stop larger than epsilon is sent");
    expect(sync.update(160, 0, .82), false, "a stopped carriage keeps to the idle heartbeat");
    expect(sync.update(240, 0, .82), true, "and refreshes once the heartbeat is due");
    expect(sync.update(248, -4, .62), true, "reverse travel is a visible change");
    expect(sync.update(256, 0, 10), true, "a relocation or restored wheel phase is sent");
    expect(sync.update(1, 0, 10), true, "a reset clock cannot suppress future payloads");

    System.out.println(
        "MotionSyncCheck: "
            + checks
            + " idle, moving, stop, reverse, epsilon and clock-reset pacing decisions passed");
  }
}
