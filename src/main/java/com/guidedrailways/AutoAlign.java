package com.guidedrailways;

/**
 * The decision behind auto align: creep to the stop, and refuse when aligning would be unsafe.
 *
 * <p>Auto align moves a train with no schedule behind it, so the interesting part is not the creep but the
 * conditions under which it must not act: the emergency brake is on, the train is already too fast to trust a
 * creep controller, or it is so far from the stop that the offset is probably wrong rather than the train.
 *
 * <p>Pure, because a mistake here moves a train. Every rule below is asserted in {@code AutoAlignCheck}.
 */
final class AutoAlign {
  /** Fastest creep speed while aligning. */
  static final double CREEP_SPEED = .3;

  /** Slowest creep speed, so the train does not stall short of the stop. */
  static final double MIN_SPEED = .05;

  /** Above this speed the train must slow down on its own before aligning is allowed to take over. */
  static final double MAX_ENTRY_SPEED = 1.0;

  /** Beyond this offset the reading is treated as wrong, and alignment refuses rather than driving blind. */
  static final double GIVE_UP = 3.0;

  /**
   * One step of the decision.
   *
   * @param active whether alignment should be driving this tick
   * @param targetSpeed signed speed to request along the direction of travel, zero when inactive
   * @param finished true when the train is inside the tolerance band and alignment is done
   */
  record Command(boolean active, double targetSpeed, boolean finished) {
    static final Command IDLE = new Command(false, 0, false);
  }

  static Command step(double offset, double speed, boolean requested, boolean emergency) {
    if (emergency) return Command.IDLE;
    if (Math.abs(offset) <= StopOffset.TOLERANCE) return new Command(false, 0, true);
    if (Math.abs(offset) > GIVE_UP) return Command.IDLE;
    if (!requested) return Command.IDLE;
    if (Math.abs(speed) > MAX_ENTRY_SPEED) return Command.IDLE;
    double target = Math.min(CREEP_SPEED, Math.max(MIN_SPEED, Math.abs(offset) * .5));
    return new Command(true, offset > 0 ? target : -target, false);
  }

  private AutoAlign() {}
}
