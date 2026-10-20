package com.guidedrailways;

public final class MotionSyncCheck {
  private static void expect(boolean actual, boolean expected) {
    if (actual != expected) throw new AssertionError("unexpected motion sync decision");
  }

  public static void main(String[] args) {
    var sync = new MotionSync();
    expect(sync.update(0, 0, 0), true);
    for (int tick = 1; tick < 100; tick++) expect(sync.update(tick, 0, 0), false);
    expect(sync.update(100, 0, 0), true);
    expect(sync.update(101, 4, .2), false);
    expect(sync.update(102, 4, .4), true);
    expect(sync.update(103, 4, .6), false);
    expect(sync.update(104, 4, .8), true);
    expect(sync.update(105, 0, .8), false);
    expect(sync.update(106, 0, .8), true);
    expect(sync.update(108, 0, .8), false);
    expect(sync.update(110, -4, .6), true);
    expect(sync.update(112, 0, .6), true);
    expect(sync.update(114, 0, 10), true); // Relocation or restored wheel phase.
    expect(sync.update(1, 0, 10), true); // A reset clock cannot suppress future packets.
    System.out.println(
        "MotionSyncCheck: idle heartbeat, motion, stop, reverse and clock reset passed");
  }
}
