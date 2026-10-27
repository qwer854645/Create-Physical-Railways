package com.guidedrailways;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts block entity data refreshes the client applies, per second and per type.
 *
 * <p>A block entity payload that arrives while docked makes the client rebuild that entity's display,
 * which is GPU work: it does not show up as a Java hotspot, so it is invisible to a sampler and to the
 * per-renderer timers. Counting arrivals is the only cheap way to see it.
 *
 * <p>Silent unless detailedLogging is on.
 */
final class SyncWatch {
  private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();
  private static long nextLog;

  static void note(Class<?> type) {
    if (!TrainConfig.DETAILED_LOGGING.get()) return;
    var name = type.getSimpleName();
    COUNTS.merge(name, 1, Integer::sum);
    long now = System.currentTimeMillis();
    if (now < nextLog) return;
    nextLog = now + 1000;
    var line = new StringBuilder("[SyncWatch]");
    for (var entry : COUNTS.entrySet()) line.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
    COUNTS.clear();
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info(line.toString());
  }

  private SyncWatch() {}
}
