package com.physicalrailways;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * The room a consist's leading axle last believed it had.
 *
 * <p>The physics step computes it every substep and the console cannot: the console is a block entity that only
 * knows its carrier. Keyed by carrier for that reason, and it expires quickly, because a stale room would show a
 * stop distance the train no longer has.
 */
final class ApproachRooms {
  /** Ticks a recorded room stays usable. The step records it several times per tick. */
  static final long LIFETIME = 10;

  private static final Map<ServerLevel, TickCache<UUID, Double>> ROOMS = new WeakHashMap<>();

  static void record(ServerLevel level, UUID carrier, double room) {
    ROOMS.computeIfAbsent(level, l -> new TickCache<>(LIFETIME))
        .put(carrier, room, level.getGameTime());
  }

  /** The last recorded room, or {@code NaN} when there is none that is still current. */
  static double room(ServerLevel level, UUID carrier) {
    var cache = ROOMS.get(level);
    var value = cache == null ? null : cache.get(carrier, level.getGameTime());
    return value == null ? Double.NaN : value;
  }

  static void tick(ServerLevel level) {
    var cache = ROOMS.get(level);
    if (cache != null) cache.tick(level.getGameTime());
  }

  static void unload(ServerLevel level) {
    ROOMS.remove(level);
  }

  private ApproachRooms() {}
}
