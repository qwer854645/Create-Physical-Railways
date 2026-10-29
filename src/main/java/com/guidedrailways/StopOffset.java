package com.guidedrailways;

/**
 * How far a train is from the stop it was aiming at.
 *
 * <p>Signed on purpose: positive means the stop is still ahead, negative means the train has gone past it. Both
 * are ordinary outcomes of a manual approach, and the console has to tell them apart — "3 blocks to go" and
 * "3 blocks past the platform" are opposite problems.
 *
 * <p>Pure, so the sign convention and the tolerance band can be asserted instead of remembered.
 */
final class StopOffset {
  /** Half-width of the band that counts as standing at the stop. */
  static final double TOLERANCE = .3;

  enum Band {
    /** Still short of the stop. */
    SHORT,
    /** Inside the tolerance band. */
    ON,
    /** Past the stop. */
    OVER
  }

  /** The offset for a stop that is still ahead by {@code distance}. */
  static double ahead(double distance) {
    return Math.abs(distance);
  }

  /** The offset for a stop the train has passed by {@code distance}. */
  static double past(double distance) {
    return -Math.abs(distance);
  }

  static Band band(double offset) {
    if (offset > TOLERANCE) return Band.SHORT;
    if (offset < -TOLERANCE) return Band.OVER;
    return Band.ON;
  }

  /** True when the offset is worth showing as a fault rather than as fine tuning. */
  static boolean wrongSide(double offset) {
    return Math.abs(offset) > TOLERANCE * 3;
  }

  private StopOffset() {}
}
