package com.guidedrailways;

/**
 * What a guided unit is allowed to believe about the room in front of it.
 *
 * <p>This exists because the same mistake was made twice in one day on the caller's side. The room a unit is
 * given is {@code min(switch distance, whatever the plan wants)}, and a train standing on a switch is told
 * there is no room in front of it at all. The stop model holds a train that has no room, and the only way to
 * clear a switch is to move, so the train never moves. Clearing the room to "no limit" fixed that, but doing
 * it on the combined value also cleared a real stop, and trains overran their platforms.
 *
 * <p>So the decision is a named function with its own checks rather than an expression buried in the physics
 * step: a switch that has already been reached imposes no limit, and nothing else is touched.
 */
final class ApproachLimit {
  /** Room at or below this means the obstacle is under the train, not in front of it. */
  static final double CREEP = .08;

  /**
   * The room a reached switch leaves, given the switch distance alone.
   *
   * <p>Anything above {@link #CREEP} is a real approach and is returned unchanged; a real approach still has
   * to be able to stop the train.
   */
  static double switchRoom(double distance) {
    return distance <= CREEP ? Double.POSITIVE_INFINITY : distance;
  }

  /**
   * The room the stop model is given, given the switch distance and the plan's own limit.
   *
   * <p>Only used where the two arrive together; the physics step keeps them apart so that the plan's limit
   * keeps its stored value.
   */
  static double combined(double switchDistance, double planLimit) {
    return Math.min(switchRoom(switchDistance), planLimit);
  }

  private ApproachLimit() {}
}
