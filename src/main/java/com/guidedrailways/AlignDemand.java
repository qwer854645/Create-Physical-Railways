package com.guidedrailways;

/**
 * What auto align asks the traction layer for, separate from the code that builds a command.
 *
 * <p>The command builder had this decision inline, mixed with signs, junctions and schedules, which made it the kind
 * of code where a change to one case silently changes another. Pulling it out gives it a name, a return value, and
 * assertions; the builder keeps the plumbing.
 *
 * <p>A decision either leaves the lever alone, replaces it, or ends the alignment. Those three are the only outcomes
 * the caller has to understand.
 */
final class AlignDemand {
  /**
   * @param stop true when alignment has finished or can no longer continue
   * @param override true when this decision replaces the lever's ceiling and notch
   * @param cap ceiling to request when overriding
   * @param notch notch to request when overriding
   */
  record Decision(boolean stop, boolean override, double cap, int notch) {
    static final Decision LEVER = new Decision(false, false, 0, 0);
    static final Decision STOP = new Decision(true, false, 0, 0);

    static Decision hold() {
      // Refused alignment must not leave the lever driving: the ceiling is pinned to zero instead.
      return new Decision(false, true, 0, 0);
    }

    static Decision creep(double target) {
      return new Decision(false, true, Math.abs(target), target > 0 ? 1 : -1);
    }
  }

  /**
   * The decision for this tick.
   *
   * <p>An unknown offset stops alignment rather than guessing: the room the physics step records expires in a few
   * ticks, and driving on a stale number is how a train creeps into a platform it cannot see.
   */
  static Decision of(double offset, double speed, boolean emergency) {
    if (Double.isNaN(offset)) return Decision.STOP;
    var align = AutoAlign.step(offset, speed, true, emergency);
    if (align.finished()) return Decision.STOP;
    if (!align.active()) return Decision.hold();
    return Decision.creep(align.targetSpeed());
  }

  private AlignDemand() {}
}
