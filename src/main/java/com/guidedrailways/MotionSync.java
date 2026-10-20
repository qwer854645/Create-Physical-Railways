package com.guidedrailways;

/** Preserve moving interpolation and the final stop update without resending idle motion. */
final class MotionSync {
  private long sent = Long.MIN_VALUE;
  private double speed, distance;

  boolean update(long tick, double nextSpeed, double nextDistance) {
    boolean first = sent == Long.MIN_VALUE || tick < sent;
    boolean changed =
        Double.compare(speed, nextSpeed) != 0 || Double.compare(distance, nextDistance) != 0;
    if (!first && tick - sent < 100 && (!changed || tick - sent < 2)) return false;
    sent = tick;
    speed = nextSpeed;
    distance = nextDistance;
    return true;
  }
}
