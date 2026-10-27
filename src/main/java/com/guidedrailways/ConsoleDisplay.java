package com.guidedrailways;

/**
 * Which display a console shows, decided without the client.
 *
 * <p>The index is what the block state carries, so the client picks a model variant instead of being
 * told to redraw: a state change costs one block update, and the value only changes when the coarse
 * state does, not on every lever tick.
 */
final class ConsoleDisplay {
  static final int IDLE = 0;
  static final int POWER = 1;
  static final int BRAKE = 2;
  static final int EMERGENCY = 3;

  static final int COUNT = 4;

  /** Emergency outranks everything: it is the one state that has to be readable from a distance. */
  static int index(boolean emergency, boolean active, boolean observing, int notch) {
    if (emergency) return EMERGENCY;
    if (active) return notch > 0 ? POWER : notch < 0 ? BRAKE : IDLE;
    // An unstaffed console with a running schedule is working, and should not look switched off.
    return observing ? POWER : IDLE;
  }

  private ConsoleDisplay() {}
}
