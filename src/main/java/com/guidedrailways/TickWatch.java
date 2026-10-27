package com.guidedrailways;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Reports server ticks that ran long, together with how much physical railway was loaded.
 *
 * <p>Player contact with a carriage is the one path this project cannot time directly: the cost sits
 * in the native solver's contact generation, outside every counter in {@link PerfCounters}. A tick
 * watchdog is the cheap way to tell an engine-side contact spike apart from a client frame drop,
 * because only the former moves this number.
 */
final class TickWatch {
  /** Ticks longer than this are worth reporting: 45 ms already misses 20 ticks per second. */
  private static final long SPIKE_MS = 45;

  private static final Map<ServerLevel, Long> START = new WeakHashMap<>();

  static void pre(ServerLevel level) {
    if (TrainConfig.DETAILED_LOGGING.get()) START.put(level, System.nanoTime());
  }

  static void post(ServerLevel level) {
    var start = START.remove(level);
    if (start == null) return;
    long ms = (System.nanoTime() - start) / 1_000_000;
    if (ms < SPIKE_MS) return;
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[TickSpike] ms={} units={} players={}",
            ms,
            VehiclePhysics.activeCount(level),
            level.players().size());
  }

  private TickWatch() {}
}
