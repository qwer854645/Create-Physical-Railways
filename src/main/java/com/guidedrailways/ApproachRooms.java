package com.guidedrailways;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

  private record Entry(double room, long until) {}

  private static final Map<String, Entry> ROOMS = new HashMap<>();

  private static String key(ServerLevel level, UUID carrier) {
    return level.dimension().location() + "@" + carrier;
  }

  static void record(ServerLevel level, UUID carrier, double room) {
    ROOMS.put(key(level, carrier), new Entry(room, level.getGameTime() + LIFETIME));
  }

  /** The last recorded room, or {@code NaN} when there is none that is still current. */
  static double room(ServerLevel level, UUID carrier) {
    var entry = ROOMS.get(key(level, carrier));
    if (entry == null) return Double.NaN;
    if (level.getGameTime() > entry.until()) {
      ROOMS.remove(key(level, carrier));
      return Double.NaN;
    }
    return entry.room();
  }

  private ApproachRooms() {}
}
