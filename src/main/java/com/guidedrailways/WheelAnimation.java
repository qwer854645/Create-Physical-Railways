package com.guidedrailways;

/** Client-only wheel phase prediction between motion packets, measured in travelled blocks. */
final class WheelAnimation {
  private boolean ready;
  private long tick;
  private double distance, speed, correction;

  void accept(double nextDistance, double nextSpeed, long now) {
    if (!Double.isFinite(nextDistance) || !Double.isFinite(nextSpeed)) return;
    double error = ready && now >= tick ? sample(now, 0) - nextDistance : 0;
    // Placement, reload and large discontinuities should not wind through an unrelated phase.
    correction = Math.abs(error) <= Math.max(2, Math.abs(nextSpeed) * .5) ? error : 0;
    distance = nextDistance;
    speed = nextSpeed;
    tick = now;
    ready = true;
  }

  double sample(long now, float partial) {
    double elapsed = Math.max(0, (double) now - tick + Math.clamp(partial, 0, 1));
    return distance + speed * elapsed / 20 + correction * Math.max(0, 1 - elapsed / 4);
  }

  boolean ready() {
    return ready;
  }
}
