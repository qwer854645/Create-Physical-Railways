package com.guidedrailways;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which physics bodies failed to read during this tick, and how often that may be reported.
 *
 * <p>Pure on purpose: the guard's policy is checked offline, while {@link BodyGuard} supplies the
 * Minecraft and Sable types.
 */
final class BodySkip {
  /** Report the first failure of a tick, then every twentieth, so a broken carriage cannot spam. */
  private static final int REPORT_EVERY = 20;

  private final Set<UUID> gone = new HashSet<>();

  /** Bodies already probed successfully this tick: probing again cannot learn anything new. */
  private final Set<UUID> known = new HashSet<>();
  private long tick = Long.MIN_VALUE;
  private int failures;

  /** Starts a new tick; a body is probed again because it may have been repaired since. */
  void begin(long now) {
    if (now == tick) return;
    tick = now;
    gone.clear();
    known.clear();
  }

  boolean gone(UUID body) {
    return gone.contains(body);
  }

  /** True when this body was already probed successfully during this tick. */
  boolean known(UUID body) {
    return known.contains(body);
  }

  void markKnown(UUID body) {
    known.add(body);
  }

  /** Returns true when this body is newly unreadable, false when it was already known. */
  boolean mark(UUID body) {
    failures++;
    return gone.add(body);
  }

  boolean shouldReport() {
    return failures > 0 && (failures == 1 || failures % REPORT_EVERY == 0);
  }

  int failures() {
    return failures;
  }
}
