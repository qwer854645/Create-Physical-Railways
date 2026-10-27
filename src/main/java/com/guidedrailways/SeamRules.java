package com.guidedrailways;

/**
 * When an axle may cross from one track edge into the next.
 *
 * <p>The transport has to advance through the node at the end of the current edge instead of
 * projecting onto it, so it only does that once the previous sample is already close to that end.
 * Being strict here is what makes a seam between two edges behave: too strict and a train at speed
 * is refused in the middle of the approach, too loose and a distant sample is advanced from.
 *
 * <p>The historical failure this table records: 37 releases logged as
 * {@code track_missing_or_out_of_range} had the surviving axle at {@code t = 0.76 to 0.92}, which
 * on a long edge is far from the end in blocks while still close in parameter space. Those samples
 * show which side of the window the gate currently falls on.
 */
final class SeamRules {
  /** Parameter window around the end of the edge that still counts as "at the end". */
  static final double WINDOW = .12;

  /** Squared block distance to the end point that also counts as "at the end". */
  static final double NEAR_SQUARED = .36;

  /** {@code endT} is 1 when travel runs along increasing edge parameter, 0 otherwise. */
  static boolean canAdvance(double previousT, double endT, double distanceSquaredToEnd) {
    return Math.abs(previousT - endT) <= WINDOW || distanceSquaredToEnd <= NEAR_SQUARED;
  }

  /**
   * Consecutive substeps an axle may fail to find its edge before the carriage is released.
   *
   * <p>A single miss is usually a projection that landed a few centimetres outside the accepted
   * deviation while crossing a seam, not a derailment. Holding the previous guides for a couple of
   * substeps lets the next projection recover; releasing on the first miss turns any one-frame
   * glitch into a derailment.
   */
  static final int MISS_LIMIT = 3;

  /**
   * A node with exactly two connections is a plain seam: the edge that was entered from and one
   * other edge. There is nothing to choose there, so refusing the only candidate because the
   * upstream geometry helper dislikes the pair leaves the axle with no edge at all.
   */
  static boolean pureSeam(int connectionCount) {
    return connectionCount == 2;
  }

  static boolean mayHold(int consecutiveMisses) {
    return consecutiveMisses < MISS_LIMIT;
  }

  private SeamRules() {}
}
