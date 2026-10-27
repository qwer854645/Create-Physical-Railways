package com.guidedrailways;

/**
 * Paces the block entity payloads that keep a moving carriage's wheels and plates animated.
 *
 * <p>The client interpolates its own animation from the last speed it was given, so the payload does not
 * have to arrive every other tick. It used to: {@code speed != lastSpeed} counted any change at all, so
 * decelerating into a station sent four bogie payloads every two ticks, forty per second, and every
 * arrival rebuilt those visuals on the client (GPU upload) exactly while the frame budget was needed for
 * the station itself. Measured with {@code [SyncWatch]}: FPS fell from about 100 to 50 on arrival.
 *
 * <p>A payload now goes out at most every {@link #MOVING_INTERVAL} ticks while something changes, and
 * only when the change is large enough to be visible. An idle carriage still refreshes slowly, so a stop
 * that lasts still ends up with the right values on every client.
 */
final class MotionSync {
  /** Ticks between payloads while the carriage is moving. */
  private static final int MOVING_INTERVAL = 8;

  /** Ticks between payloads once nothing has changed, so a long stop still settles correctly. */
  private static final int IDLE_INTERVAL = 100;

  /** A speed change below this is not visible in the wheel animation. */
  private static final double SPEED_EPSILON = 0.05;

  /** A distance change below this is far below one texel on a wheel that size. */
  private static final double DISTANCE_EPSILON = 0.05;

  private long sent = Long.MIN_VALUE;
  private double speed, distance;

  boolean update(long tick, double nextSpeed, double nextDistance) {
    boolean first = sent == Long.MIN_VALUE || tick < sent;
    boolean changed =
        Math.abs(speed - nextSpeed) > SPEED_EPSILON
            || Math.abs(distance - nextDistance) > DISTANCE_EPSILON;
    int interval = changed ? MOVING_INTERVAL : IDLE_INTERVAL;
    if (!first && tick - sent < interval) return false;
    sent = tick;
    speed = nextSpeed;
    distance = nextDistance;
    return true;
  }
}
