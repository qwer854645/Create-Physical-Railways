package com.guidedrailways;

/**
 * Which console lamps are lit, decided without the client.
 *
 * <p>The lamps follow the vehicle and the lever, not the seat: an unstaffed carriage keeps showing what
 * it was told to do, and only a console with nothing to drive goes dark.
 *
 * <pre>
 *   all dark      nothing bound to this console
 *   left green    traction
 *   middle amber  standby, no notch
 *   right red     braking
 *   all red       emergency
 * </pre>
 *
 * <p>They are drawn by the block entity renderer every frame. Putting them in the block state made
 * every change cost a client chunk rebuild, which forced a throttle and made the lamps lag behind a
 * quickly moved lever.
 */
final class ConsoleDisplay {
  static final int LEFT = 1;
  static final int MIDDLE = 2;
  static final int RIGHT = 4;

  static final int GREEN = 0x74D27C;
  static final int AMBER = 0xE8AE58;
  static final int RED = 0xF3664E;

  /** Bits of {@link #LEFT}, {@link #MIDDLE} and {@link #RIGHT} that are lit. */
  static int lampMask(boolean bound, boolean emergency, int notch) {
    if (!bound) return 0;
    if (emergency) return LEFT | MIDDLE | RIGHT;
    return notch > 0 ? LEFT : notch < 0 ? RIGHT : MIDDLE;
  }

  /** Colour of a lit lamp; emergency paints every lit lamp red. */
  static int colour(int lamp, boolean emergency) {
    if (emergency) return RED;
    if ((lamp & LEFT) != 0) return GREEN;
    if ((lamp & MIDDLE) != 0) return AMBER;
    return RED;
  }

  private ConsoleDisplay() {}
}
