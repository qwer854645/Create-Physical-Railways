package com.guidedrailways;

import java.util.WeakHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Remembers, briefly, why a consist was last refused at a junction.
 *
 * <p>The refusal is decided deep in the routing callback and the console is a block entity that has no way to
 * learn it: the console only knows its carrier, while the refusal is recorded against the consist's reservation
 * identity, which is the smallest bogie id of the consist. So the reason is kept here, keyed by that identity,
 * and the console inverts the same derivation through {@link #identityOf}.
 *
 * <p>Entries expire: a reason that is no longer current must not be shown, or the console would keep claiming a
 * junction is held long after it was released.
 */
final class SwitchBlockedReasons {
  /** Ticks a recorded reason stays current. The routing callback asks every substep. */
  static final long LIFETIME = 60;

  private static final Map<ServerLevel, TickCache<UUID, String>> RECORDED = new WeakHashMap<>();

  static void record(ServerLevel level, UUID identity, String reason) {
    RECORDED.computeIfAbsent(level, l -> new TickCache<>(LIFETIME))
        .put(identity, reason, level.getGameTime());
  }

  /** The current reason, or null when there is none or it has aged out. */
  static String reason(ServerLevel level, UUID identity) {
    var cache = RECORDED.get(level);
    return cache == null ? null : cache.get(identity, level.getGameTime());
  }

  static void tick(ServerLevel level) {
    var cache = RECORDED.get(level);
    if (cache != null) cache.tick(level.getGameTime());
  }

  static void unload(ServerLevel level) {
    RECORDED.remove(level);
  }

  /**
   * The reservation identity of the consist that contains this carrier.
   *
   * <p>Mirrors {@link RouteMemory#reservationIdentity}: the smallest bogie id of the consist, or the carrier
   * itself when the consist has no guided axles. Duplicating the rule is deliberate — the console cannot build a
   * {@code RouteMemory}, and both sides must agree on the identity or the lookup silently never matches.
   */
  static UUID identityOf(ServerLevel level, UUID carrier) {
    UUID least = null;
    for (var group : TrainConsists.get(level).groups()) {
      boolean member = false;
      for (var unit : group.units())
        if (unit.carrier.getUniqueId().equals(carrier)) {
          member = true;
          break;
        }
      if (!member) continue;
      for (var unit : group.units()) {
        var bogie = unit.bogie.getUniqueId();
        if (least == null || bogie.compareTo(least) < 0) least = bogie;
      }
    }
    return least == null ? carrier : least;
  }

  private SwitchBlockedReasons() {}
}
