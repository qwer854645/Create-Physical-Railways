package com.guidedrailways;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Opt-in wall-clock measurements, not a total server tick or frame-rate benchmark.
 *
 * <p>Collecting costs two {@code System.nanoTime()} calls per measured section, so every section
 * is skipped entirely while {@code performanceLogging} is off: {@link #begin()} returns 0 and the
 * accumulators ignore it. Sections nest — {@code switchMs} is included in {@code stepMs} — so the
 * split line attributes time, it does not partition it.
 */
final class PerfCounters {
  private static final class Stats {
    long next, steps, total, max, queries, checks, builds, checkTime, maxCheck;

    long ticks, before, after, maxBefore, maxAfter;
    long contacts, maxContacts, switchCost, switchCalls, maxSwitch, coupler, maxCoupler;
  }

  private static final Map<ServerLevel, Stats> STATS = new WeakHashMap<>();

  /** Zero when performance logging is off, so {@link #elapsed(long)} stays free. */
  static long begin() {
    return TrainConfig.PERFORMANCE_LOGGING.get() ? System.nanoTime() : 0L;
  }

  static long elapsed(long start) {
    return start == 0 ? 0 : System.nanoTime() - start;
  }

  static void step(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.steps++;
    s.total += nanos;
    s.max = Math.max(s.max, nanos);
  }

  /** Native bogie/bogie contact reconciliation inside one physics substep. */
  static void contacts(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.contacts += nanos;
    s.maxContacts = Math.max(s.maxContacts, nanos);
  }

  /** One junction/stop-distance query; counted separately because it walks the track graph. */
  static void switchQuery(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.switchCalls++;
    s.switchCost += nanos;
    s.maxSwitch = Math.max(s.maxSwitch, nanos);
  }

  /** Coupler joint reconciliation inside one physics substep. */
  static void coupler(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.coupler += nanos;
    s.maxCoupler = Math.max(s.maxCoupler, nanos);
  }

  static void before(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.before += nanos;
    s.maxBefore = Math.max(s.maxBefore, nanos);
  }

  static void after(ServerLevel level, long nanos) {
    if (nanos == 0) return;
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    s.ticks++;
    s.after += nanos;
    s.maxAfter = Math.max(s.maxAfter, nanos);
  }

  static void query(ServerLevel level) {
    if (TrainConfig.PERFORMANCE_LOGGING.get())
      STATS.computeIfAbsent(level, l -> new Stats()).queries++;
  }

  static void index(ServerLevel level, long nanos, boolean rebuilt) {
    if (TrainConfig.PERFORMANCE_LOGGING.get()) {
      var s = STATS.computeIfAbsent(level, l -> new Stats());
      s.checks++;
      if (rebuilt) s.builds++;
      s.checkTime += nanos;
      s.maxCheck = Math.max(s.maxCheck, nanos);
    }
  }

  static void report(ServerLevel level, int bogies) {
    if (!TrainConfig.PERFORMANCE_LOGGING.get()) {
      STATS.remove(level);
      return;
    }
    var s = STATS.computeIfAbsent(level, l -> new Stats());
    if (level.getGameTime() < s.next) return;
    s.next = level.getGameTime() + 20;
    var log = org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID);
    if (s.steps > 0 && bogies > 0)
      log.info(
          "[GuidedPerf] bogies={} steps={} meanMs={} maxMs={} pathQueries={} indexChecks={} indexCheckTotalMs={} indexCheckMaxMs={} indexRebuilds={} scope=java_guidance_only",
          bogies,
          s.steps,
          mean(s.total, s.steps),
          s.max / 1e6,
          s.queries,
          s.checks,
          s.checkTime / 1e6,
          s.maxCheck / 1e6,
          s.builds);
    if (s.steps > 0 || s.ticks > 0)
      log.info(
          "[GuidedPerfSplit] bogies={} stepMs={} contactsMs={} maxContactsMs={} switchMs={} switchCalls={} maxSwitchMs={} couplerMs={} maxCouplerMs={} beforeMs={} maxBeforeMs={} afterMs={} maxAfterMs={} ticks={} scope=java_guidance_only",
          bogies,
          mean(s.total, s.steps),
          mean(s.contacts, s.steps),
          s.maxContacts / 1e6,
          mean(s.switchCost, s.steps),
          s.switchCalls,
          s.maxSwitch / 1e6,
          mean(s.coupler, s.steps),
          s.maxCoupler / 1e6,
          mean(s.before, s.ticks),
          s.maxBefore / 1e6,
          mean(s.after, s.ticks),
          s.maxAfter / 1e6,
          s.ticks);
    s.steps = s.total = s.max = s.queries = s.checks = s.builds = s.checkTime = s.maxCheck = 0;
    s.ticks = s.before = s.after = s.maxBefore = s.maxAfter = 0;
    s.contacts = s.maxContacts = s.switchCost = s.switchCalls = s.maxSwitch = 0;
    s.coupler = s.maxCoupler = 0;
  }

  /** Mean milliseconds per sample; {@code count} is never taken before it is incremented. */
  private static double mean(long nanos, long count) {
    return count == 0 ? 0 : nanos / (double) count / 1e6;
  }

  static void unload(ServerLevel level) {
    STATS.remove(level);
  }
}
