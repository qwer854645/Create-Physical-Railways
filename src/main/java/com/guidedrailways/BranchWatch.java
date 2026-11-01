package com.guidedrailways;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Reports a consist whose axles disagree about a junction.
 *
 * <p>Two bogies on one carriage must take the same branch, so when one consist records two different branches at the
 * same node inside {@link #WINDOW} ticks, something in the decision is wrong. This only reports: the fix has to be
 * chosen from the evidence, and the evidence is exactly which decision each axle took.
 */
final class BranchWatch {
  /** Axles of one consist meet the same junction well inside this window. */
  static final long WINDOW = 40;

  private record Key(UUID identity, int node) {}
  private record Seen(int to, long at) {}

  private static final Map<ServerLevel, TickCache<Key, Seen>> LAST = new WeakHashMap<>();

  static void record(ServerLevel level, UUID identity, int node, int to) {
    if (to < 0) return;
    long now = level.getGameTime();
    var cache = LAST.computeIfAbsent(level, l -> new TickCache<>(WINDOW));
    var key = new Key(identity, node);
    var previous = cache.get(key, now);
    cache.put(key, new Seen(to, now), now);
    if (previous != null && previous.to() != to)
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[ConsistSplit] node={} identity={} first={} second={} apart={}tick",
              node,
              identity,
              previous.to(),
              to,
              now - previous.at());
  }

  static void tick(ServerLevel level) {
    var cache = LAST.get(level);
    if (cache != null) cache.tick(level.getGameTime());
  }

  static void unload(ServerLevel level) {
    LAST.remove(level);
  }

  private BranchWatch() {}
}
