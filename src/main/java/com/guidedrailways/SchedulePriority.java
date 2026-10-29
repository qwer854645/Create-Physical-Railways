package com.guidedrailways;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * The scheduling priority of each consist, kept where the routing callback can read it.
 *
 * <p>The priority lives on the schedule, but the switch queue is decided inside a routing callback that only has
 * a consist identity. Recording it here, refreshed while the consist has a schedule, avoids threading a new
 * parameter through the whole pass chain for a value that changes when a schedule changes, not per call.
 */
final class SchedulePriority {
  /** Ticks a recorded priority stays current. The preparing consist refreshes it every tick. */
  static final long LIFETIME = 40;

  private record Entry(int priority, long until) {}

  private static final Map<String, Entry> RECORDED = new HashMap<>();

  private static String key(ServerLevel level, UUID identity) {
    return level.dimension().location() + "@" + identity;
  }

  static void record(ServerLevel level, UUID identity, int priority) {
    RECORDED.put(key(level, identity), new Entry(priority, level.getGameTime() + LIFETIME));
  }

  /** The current priority, or the default when a consist has no schedule or the record lapsed. */
  static int of(ServerLevel level, UUID identity) {
    var entry = RECORDED.get(key(level, identity));
    if (entry == null) return SwitchQueue.DEFAULT_PRIORITY;
    if (level.getGameTime() > entry.until()) {
      RECORDED.remove(key(level, identity));
      return SwitchQueue.DEFAULT_PRIORITY;
    }
    return entry.priority();
  }

  private SchedulePriority() {}
}
