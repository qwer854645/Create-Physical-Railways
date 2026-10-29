package com.guidedrailways;

import java.util.WeakHashMap;
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

  static int load(net.minecraft.nbt.CompoundTag data) {
    return data.contains("Priority", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
        ? Math.clamp(data.getInt("Priority"), 0, SwitchQueue.MAX_PRIORITY)
        : SwitchQueue.DEFAULT_PRIORITY;
  }

  static void save(net.minecraft.nbt.CompoundTag data, int priority) {
    data.putInt("Priority", Math.clamp(priority, 0, SwitchQueue.MAX_PRIORITY));
  }

  private static final Map<ServerLevel, TickCache<UUID, Integer>> RECORDED = new WeakHashMap<>();

  static void record(ServerLevel level, UUID identity, int priority) {
    RECORDED.computeIfAbsent(level, l -> new TickCache<>(LIFETIME))
        .put(identity, priority, level.getGameTime());
  }

  /** The current priority, or the default when a consist has no schedule or the record lapsed. */
  static int of(ServerLevel level, UUID identity) {
    var cache = RECORDED.get(level);
    var priority = cache == null ? null : cache.get(identity, level.getGameTime());
    return priority == null ? SwitchQueue.DEFAULT_PRIORITY : priority;
  }

  static void tick(ServerLevel level) {
    var cache = RECORDED.get(level);
    if (cache != null) cache.tick(level.getGameTime());
  }

  static void unload(ServerLevel level) {
    RECORDED.remove(level);
  }

  private SchedulePriority() {}
}
