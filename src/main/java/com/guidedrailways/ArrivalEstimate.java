package com.guidedrailways;

/**
 * Live route progress, in game ticks. Unknown is Create's native -1; zero requires real arrival.
 */
public final class ArrivalEstimate {
  private Object journey;
  private long tick;
  private double remaining, speed, rate;
  private int observedTicks;

  public int sample(Object journey, long tick, double remaining, double speed, boolean travelling) {
    if (journey == null
        || !Double.isFinite(remaining)
        || remaining < 0
        || !Double.isFinite(speed)
        || speed < 0
        || !travelling
        || speed <= .12) {
      reset();
      return -1;
    }
    long elapsed = tick - this.tick;
    double progress = this.remaining - remaining;
    if (this.journey != journey
        || elapsed <= 0
        || elapsed > 20
        || progress <= .0001
        || progress > Math.max(.25, (Math.max(this.speed, speed) + 2) * elapsed / 20.0 * 2)) {
      this.journey = journey;
      this.tick = tick;
      this.remaining = remaining;
      this.speed = speed;
      rate = 0;
      observedTicks = 0;
      return -1;
    }
    double measured = progress / elapsed;
    rate = observedTicks == 0 ? measured : rate + (measured - rate) * -Math.expm1(-elapsed / 40.0);
    observedTicks = Math.min(20, observedTicks + (int) elapsed);
    this.tick = tick;
    this.remaining = remaining;
    this.speed = speed;
    if (observedTicks < 20 || rate <= .006) return -1;
    return (int) Math.clamp(Math.ceil(remaining / rate), 1, Integer.MAX_VALUE);
  }

  public void reset() {
    journey = null;
    tick = 0;
    remaining = speed = rate = 0;
    observedTicks = 0;
  }
}
