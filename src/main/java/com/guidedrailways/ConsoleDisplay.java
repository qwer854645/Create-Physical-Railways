package com.guidedrailways;

/**
 * What the console lamps show, decided without the client.
 *
 * <p>The lamps follow the train, not the seat: a carriage left standing keeps showing what it was last
 * told to do, because a driver walking away does not switch the vehicle off. Three fixed lamps carry
 * the state, and all of them go dark only when the console cannot drive anything at all.
 *
 * <pre>
 *   all off        nothing bound to this console
 *   left green     traction
 *   middle amber   standby, no notch
 *   right red      braking
 *   all red        emergency
 * </pre>
 */
final class ConsoleDisplay {
  static final int OFF = 0;
  static final int POWER = 1;
  static final int STANDBY = 2;
  static final int BRAKE = 3;
  static final int EMERGENCY = 4;

  static final int COUNT = 5;

  /** {@code bound} means this console resolves to a live physical body it can actually drive. */
  static int index(boolean bound, boolean emergency, int notch) {
    if (!bound) return OFF;
    if (emergency) return EMERGENCY;
    return notch > 0 ? POWER : notch < 0 ? BRAKE : STANDBY;
  }

  private ConsoleDisplay() {}
}
