package com.guidedrailways;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-second cost of each of this mod's block entity renderers.
 *
 * <p>The frame probe can say which render stage is slow but not which renderer inside it, and the
 * stage that keeps reporting the slowest segment is the one block entity renderers run in. This splits
 * that stage by renderer so the next report names one instead of a range.
 *
 * <p>Silent unless detailedLogging is on; the counters are two field adds per draw otherwise.
 */
final class BerPerf {
  private record Stat(double worstMs, double totalMs, int count) {}

  private static final Map<String, double[]> STATS = new LinkedHashMap<>();
  private static long nextLog;

  static long start() {
    return TrainConfig.DETAILED_LOGGING.get() ? System.nanoTime() : 0;
  }

  static void record(String name, long start) {
    if (start == 0) return;
    double ms = (System.nanoTime() - start) / 1e6;
    var stat = STATS.computeIfAbsent(name, k -> new double[3]);
    stat[0] = Math.max(stat[0], ms);
    stat[1] += ms;
    stat[2] += 1;
  }

  static void report() {
    long now = System.currentTimeMillis();
    if (now < nextLog || STATS.isEmpty()) return;
    nextLog = now + 1000;
    var line = new StringBuilder("[BerPerf]");
    for (var entry : STATS.entrySet()) {
      var s = entry.getValue();
      line.append(' ')
          .append(entry.getKey())
          .append(":mean=")
          .append(String.format(java.util.Locale.ROOT, "%.3f", s[1] / Math.max(1, s[2])))
          .append(",max=")
          .append(String.format(java.util.Locale.ROOT, "%.2f", s[0]))
          .append(",calls=")
          .append((long) s[2]);
    }
    STATS.clear();
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info(line.toString());
  }

  private BerPerf() {}
}
