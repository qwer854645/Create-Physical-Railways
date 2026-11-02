package com.physicalrailways;

import java.util.concurrent.atomic.LongAdder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Actual encoded payload totals across clients; excludes transport headers. Opt-in only. */
final class TrainSyncMetrics {
  private static volatile boolean enabled;
  private static final LongAdder BYTES = new LongAdder(), FULL = new LongAdder(),
      DELTA = new LongAdder(), SIGNAL = new LongAdder();

  static void encoded(CompoundTag tag, int bytes) {
    if (!enabled) return;
    BYTES.add(bytes);
    (tag.getBoolean("SignalOnly") ? SIGNAL : tag.getBoolean("SyncDelta") ? DELTA : FULL).increment();
  }

  static void report(ServerLevel level) {
    enabled = TrainConfig.SPEC.isLoaded() && TrainConfig.PERFORMANCE_LOGGING.get();
    if (!enabled || level.dimension() != Level.OVERWORLD
        || level.getGameTime() % 100 != 0) return;
    org.slf4j.LoggerFactory.getLogger(PhysicalRailways.ID).info(
        "[TrainSyncPerf] windowTicks=100 payloadBytes={} full={} delta={} signal={}",
        BYTES.sumThenReset(), FULL.sumThenReset(), DELTA.sumThenReset(), SIGNAL.sumThenReset());
  }

  static void reset(ServerLevel level) {
    if (level.dimension() != Level.OVERWORLD) return;
    enabled = false;
    BYTES.reset(); FULL.reset(); DELTA.reset(); SIGNAL.reset();
  }
}
