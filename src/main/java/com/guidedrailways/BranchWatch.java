package com.guidedrailways;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

  private record Seen(int to, long at) {}

  private static final Map<String, Seen> LAST = new HashMap<>();

  private static String key(ServerLevel level, UUID identity, int node) {
    return level.dimension().location() + "@" + identity + "@" + node;
  }

  static void record(ServerLevel level, UUID identity, int node, int to) {
    if (to < 0) return;
    long now = level.getGameTime();
    var previous = LAST.put(key(level, identity, node), new Seen(to, now));
    if (previous != null && previous.to() != to && now - previous.at() <= WINDOW)
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[ConsistSplit] node={} identity={} first={} second={} apart={}tick",
              node,
              identity,
              previous.to(),
              to,
              now - previous.at());
  }

  private BranchWatch() {}
}
