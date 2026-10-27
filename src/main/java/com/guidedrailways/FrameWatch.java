package com.guidedrailways;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Splits slow client time into tick work and frame work.
 *
 * <p>The server side measured normal around carriages and the window stays focused, so the stutter
 * has to be inside the client. Reporting the two halves separately says whether the time goes into
 * the client tick (entities, block entities, Sable updates) or into drawing, which is the difference
 * between fixing our code and reducing what is drawn.
 */
final class FrameWatch {
  /** A frame this slow is already under 25 fps; the point is to catch mild stutter too. */
  private static final long SPIKE_MS = 40;

  private static final long WINDOW_NANOS = 5_000_000_000L;

  private static long frameStart;
  private static long tickStart;

  private static long windowStart;
  private static int frames;
  private static long frameTotal, frameWorst;
  private static long tickTotal, tickWorst;
  private static int ticks;
  private static Boolean enabled;
  private static long gcLast;
  private static long lastGameTime = -1;
  private static long lastGameSample;
  private static long stageStart;
  private static String stageName;
  private static String worstStage;
  private static long worstStageNanos;

  static void register() {
    NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> framePre());
    NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> framePost());
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> tickPre());
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tickPost());
    NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> stage(e));
  }

  /**
   * The first frames run before the server config exists, so the probe must not assume it can read
   * it; it retries until the value is available and then caches it.
   */
  private static boolean enabled() {
    if (enabled == null) {
      try {
        enabled = TrainConfig.DETAILED_LOGGING.get();
      } catch (RuntimeException notLoadedYet) {
        return false;
      }
    }
    return enabled;
  }

  private static void framePre() {
    if (enabled()) frameStart = System.nanoTime();
  }

  private static void framePost() {
    if (frameStart == 0) return;
    long nanos = System.nanoTime() - frameStart;
    frameStart = 0;
    long ms = nanos / 1_000_000;
    if (ms >= SPIKE_MS) spike(ms);
    frames++;
    frameTotal += nanos;
    frameWorst = Math.max(frameWorst, nanos);
    window();
  }

  private static void tickPre() {
    tickStart = System.nanoTime();
  }

  private static void tickPost() {
    if (tickStart == 0) return;
    long nanos = System.nanoTime() - tickStart;
    tickStart = 0;
    ticks++;
    tickTotal += nanos;
    tickWorst = Math.max(tickWorst, nanos);
  }

  /** One line per window: average and worst of both halves, so a slow half cannot hide. */
  private static void window() {
    long now = System.nanoTime();
    if (windowStart == 0) windowStart = now;
    if (now - windowStart < WINDOW_NANOS || frames == 0) return;
    double seconds = (now - windowStart) / 1e9;
    long gcTime = gcMillis();
    // Server tick rate, measured from the client: the integrated server's tick counter over wall time.
    // Frame time alone cannot distinguish "the client is slow" from "the world is ticking slowly".
    double tps = -1;
    var level = Minecraft.getInstance().level;
    if (level != null) {
      long gameTime = level.getGameTime();
      if (lastGameTime >= 0 && lastGameSample > 0) {
        double elapsed = (now - lastGameSample) / 1e9;
        if (elapsed > 0.05) tps = (gameTime - lastGameTime) / elapsed;
      }
      lastGameTime = gameTime;
      lastGameSample = now;
    }
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[ClientPerf] fps={} tps={} frames={} frameAvgMs={} frameWorstMs={} tickAvgMs={} tickWorstMs={} gcMs={} worstStage={} worstStageMs={} focused={}",
            Math.round(frames / seconds),
            tps < 0 ? "?" : String.format(java.util.Locale.ROOT, "%.1f", tps),
            frames,
            frameTotal / (double) frames / 1e6,
            frameWorst / 1e6,
            ticks == 0 ? 0 : tickTotal / (double) ticks / 1e6,
            tickWorst / 1e6,
            gcTime - gcLast,
            worstStage == null ? "-" : worstStage,
            worstStageNanos / 1e6,
            Minecraft.getInstance().isWindowActive());
    gcLast = gcTime;
    windowStart = now;
    frames = 0;
    frameTotal = frameWorst = 0;
    ticks = 0;
    tickTotal = tickWorst = 0;
    worstStage = null;
    worstStageNanos = 0;
  }

  /**
   * Attributes time to the render stage it was spent in. A slow frame that is not tick work and not
   * GC has to sit in one of these segments, and naming that segment decides who can fix it.
   *
   * <p>The gap after the last stage runs to the next frame's first stage, so it contains the frame
   * cap wait rather than level work; it is skipped instead of being reported as the worst segment.
   */
  private static void stage(RenderLevelStageEvent event) {
    long now = System.nanoTime();
    boolean first = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
    if (stageStart != 0 && !first) {
      long spent = now - stageStart;
      if (spent > worstStageNanos) {
        worstStageNanos = spent;
        worstStage = stageName;
      }
    }
    stageStart = now;
    stageName = String.valueOf(event.getStage());
  }
  private static void spike(long ms) {
    var client = Minecraft.getInstance();
    var level = client.level;
    var player = client.player;
    boolean onBody = false;
    if (level != null && player != null) {
      // Standing on a body puts the feet block inside it, while the head may be outside.
      var feet = player.blockPosition();
      onBody = contains(level, feet) || contains(level, feet.below()) || contains(level, feet.above());
    }
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[FrameSpike] ms={} onBody={} focused={} paused={} pos={}",
            ms,
            onBody,
            client.isWindowActive(),
            client.isPaused(),
            player == null ? "?" : player.blockPosition().toShortString());
  }


  /** Total collection time in milliseconds across all collectors; the delta per window is the cost. */
  private static long gcMillis() {
    long total = 0;
    for (var bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
      total += Math.max(0, bean.getCollectionTime());
    return total;
  }
  private static boolean contains(
      net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
    return dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, pos) != null;
  }

  private FrameWatch() {}
}
