package com.guidedrailways;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who has been waiting for a switch, in the order it started waiting.
 *
 * <p>Access used to be plain first-come-first-served: a consist that failed to claim simply asked
 * again next substep, and the consist holding the switch refreshed its claim forever. That is fair
 * enough for two trains but it can starve a third one indefinitely and leaves a real standstill with
 * no trace in the log. The queue records intents so a freed switch goes to the oldest waiter, and so
 * a long wait can be reported.
 *
 * <p>A live claim is never revoked here: a consist may already be standing on the switch, and taking
 * the passage away from it would strand the carriage on the wrong side of the points.
 */
final class SwitchQueue {
  record Key(UUID graph, int node) {}

  /** An intent that is not refreshed for this many ticks lapses, so abandoned waits do not linger. */
  static final long INTENT_TICKS = 40;

  /** Waiting this long is reported: past this point a human should look at the junction. */
  static final long STARVE_TICKS = 60;

  record Wait(UUID train, int from, int to, long since, long seen) {}

  private final Map<SwitchReservations.Key, LinkedHashMap<UUID, Wait>> queues = new LinkedHashMap<>();

  /** Records or refreshes an intent; the original start tick is kept so order reflects arrival. */
  void wait(SwitchReservations.Key key, UUID train, int from, int to, long now) {
    var queue = queues.computeIfAbsent(key, k -> new LinkedHashMap<>());
    var existing = queue.get(train);
    long since = existing == null ? now : existing.since();
    queue.put(train, new Wait(train, from, to, since, now));
    prune(queue, now);
  }

  /** The oldest still-live waiter, or null when nobody is queued. */
  Wait head(SwitchReservations.Key key, long now) {
    var queue = queues.get(key);
    if (queue == null) return null;
    prune(queue, now);
    for (var wait : queue.values()) return wait;
    return null;
  }

  int size(SwitchReservations.Key key, long now) {
    var queue = queues.get(key);
    if (queue == null) return 0;
    prune(queue, now);
    return queue.size();
  }

  /** Ticks the oldest waiter has waited, or -1 when nobody is waiting. */
  long oldestWait(SwitchReservations.Key key, long now) {
    var head = head(key, now);
    return head == null ? -1 : now - head.since();
  }

  void clear(SwitchReservations.Key key, UUID train) {
    var queue = queues.get(key);
    if (queue == null) return;
    queue.remove(train);
    if (queue.isEmpty()) queues.remove(key);
  }

  private static void prune(LinkedHashMap<UUID, Wait> queue, long now) {
    queue.values().removeIf(wait -> now - wait.seen() > INTENT_TICKS);
  }
}
