package com.guidedrailways;

import java.util.*;

/** A consist owns a passage, including reverse traversal, until its last axle clears it. */
final class SwitchReservations {
  record Key(UUID graph, int node) {}

  private record Claim(UUID train, int from, int to, long expires) {}

  private final Map<Key, Claim> claims = new HashMap<>();
  private final SwitchQueue queue = new SwitchQueue();
  private static final Map<Key, Long> REPORTED = new HashMap<>();

  /** Longest a claim survives without being refreshed; also the recovery path for a lost holder. */
  static final long HOLD_TICKS = 40;

  boolean claim(Key key, UUID train, int from, int to, long now) {
    claims.entrySet().removeIf(e -> e.getValue().expires <= now);
    var old = claims.get(key);
    if (old != null && old.train.equals(train)) {
      if (same(old, from, to)) {
        // The holder keeps its passage. It may already stand on the switch, so a waiter can never take
        // the claim away from it inside this method.
        claims.put(key, new Claim(train, from, to, now + HOLD_TICKS));
        return true;
      }
      // Same consist, different passage. A fresh claim means its axles are still crossing that branch,
      // so it may not take another one. A claim that nobody has refreshed for half its life belongs to a
      // crossing that is over: refusing the newer passage there left the consist holding a claim it
      // could not use, and the recorded case sat at one node for 264 ticks instead of going anywhere.
      if (old.expires - now > HOLD_TICKS / 2) return false;
      claims.put(key, new Claim(train, from, to, now + HOLD_TICKS));
      queue.clear(key, train);
      return true;
    }
    if (old != null) {
      queue.wait(key, train, from, to, now);
      report(key, train, now);
      return false;
    }
    var head = queue.head(key, now);
    if (head != null && !head.train().equals(train)) {
      // The switch is free, but somebody has been waiting longer: keep the arrival order instead of
      // letting whoever asks first in the same tick jump the queue.
      queue.wait(key, train, from, to, now);
      report(key, train, now);
      return false;
    }
    queue.clear(key, train);
    claims.put(key, new Claim(train, from, to, now + HOLD_TICKS));
    return true;
  }

  boolean owns(Key key, UUID train, int from, int to, long now) {
    var old = claims.get(key);
    return old != null && old.expires > now && old.train.equals(train) && same(old, from, to);
  }

  /** Reports a standstill so a junction nobody can cross leaves a trace instead of just stopping. */
  private void report(Key key, UUID train, long now) {
    long oldest = queue.oldestWait(key, now);
    if (oldest < SwitchQueue.STARVE_TICKS) return;
    // Throttled per node: the passage is asked for every substep, and the point is one line per
    // stalled junction, not one per attempt.
    var last = REPORTED.getOrDefault(key, Long.MIN_VALUE);
    if (now - last < 60) return;
    REPORTED.put(key, now);
    var log = org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID);
    var head = queue.head(key, now);
    log.info(
        "[SwitchQueue] node={} waiters={} asking={} head={} waited={}tick",
        key.node(),
        queue.size(key, now),
        train,
        head == null ? "?" : head.train(),
        oldest);
  }

  private static boolean same(Claim c, int from, int to) {
    return c.from == from && c.to == to || c.from == to && c.to == from;
  }
}
