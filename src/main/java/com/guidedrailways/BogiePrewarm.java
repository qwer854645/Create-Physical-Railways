package com.guidedrailways;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/**
 * Bakes a bogie model before its first visible frame.
 *
 * <p>Measured cold draws cost 14 to 53 ms when the model was trivial, and one recorded frame cost
 * 340 ms inside the renderer, which is what the player feels as an occasional hitch next to a train.
 * {@code [BogieRender]} keeps reporting the worst frame, so this stays verifiable.
 *
 * <p>A bogie whose style is still queued is not drawn for that frame. The bake runs on the next client
 * tick, so the carriage shows up one tick later instead of stalling the frame. The data tag is kept
 * with the request because Create's style renderer builds its visual from it: warming with an empty
 * tag would bake a different instance than the one the real draw needs.
 */
final class BogiePrewarm {
  /** Time one client tick may spend baking before the rest waits for the next tick. */
  private static final long BUDGET_NANOS = 4_000_000L;

  /** Ticks after which a never-baked style is drawn anyway, so a failure cannot hide a carriage. */
  private static final long GIVE_UP_TICKS = 40;

  private static final Map<Long, CompoundTag> PENDING = new java.util.HashMap<>();
  private static final Map<Long, Long> REQUESTED_AT = new java.util.HashMap<>();
  private static final Map<Long, Boolean> DONE = new java.util.HashMap<>();

  /** Variant that each queued position needs; the bake needs both the style and the data. */
  private static final Map<Long, BogeyVariant> VARIANTS = new java.util.HashMap<>();

  private static final com.simibubi.create.content.trains.bogey.StandardBogeyRenderer.Small
      FALLBACK =
          new com.simibubi.create.content.trains.bogey.StandardBogeyRenderer.Small();

  private static long warmNanos;
  private static int warmCount;
  private static long tick;

  static void request(long key, BogeyVariant variant, CompoundTag data) {
    if (variant == null) return;
    if (DONE.containsKey(key)) return;
    // The renderer asks on every frame until the bake lands; the data copy below is not free.
    if (PENDING.containsKey(key)) return;
    VARIANTS.putIfAbsent(key, variant);
    PENDING.putIfAbsent(key, data == null ? new CompoundTag() : data.copy());
    REQUESTED_AT.putIfAbsent(key, tick);
  }

  /** True when this style may be drawn without paying the first-draw cost. */
  static boolean warmed(long key) {
    if (DONE.containsKey(key)) return true;
    var since = REQUESTED_AT.get(key);
    return since != null && tick - since >= GIVE_UP_TICKS;
  }

  /** True while a request is queued, so the renderer can skip a single frame. */
  static boolean pending(long key) {
    return PENDING.containsKey(key);
  }

  /** Bakes queued styles until the per-tick budget is spent. */
  static void tick() {
    tick++;
    if (PENDING.isEmpty()) return;
    // Snapshot the keys first: IdentityHashMap invalidates map entries as soon as they are removed,
    // so reading a key back out of an entry after removal throws.
    var queued = new java.util.ArrayList<Long>(PENDING.keySet());
    long spent = 0;
    for (var key : queued) {
      if (spent >= BUDGET_NANOS) break;
      var data = PENDING.remove(key);
      if (data == null) continue;
      var variant = VARIANTS.remove(key);
      if (variant == null) continue;
      spent += bake(key, variant, data);
    }
  }

  private static long bake(long key, BogeyVariant variant, CompoundTag data) {
    long start = System.nanoTime();
    try {
      var pose = new PoseStack();
      pose.translate(.5, variant.renderY, .5);
      var byteBuffer = new ByteBufferBuilder(8192);
      try {
        var buffers = MultiBufferSource.immediate(byteBuffer);
        // Default bogeys render through Create's standard renderer, not the native style path, so the
        // bake has to use the same one or it warms the wrong thing.
        if (!NativeBogeyModels.render(variant, data, 0, 0, pose, buffers, 0xF000F0, 0))
          FALLBACK.render(data, 0, 0, pose, buffers, 0xF000F0, 0, false);
        // Submit once so the shader pipeline and vertex upload happen here, not on a visible frame.
        buffers.endBatch();
      } finally {
        byteBuffer.close();
      }
    } catch (RuntimeException failure) {
      DONE.put(key, Boolean.FALSE);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("[BogiePrewarm] could not bake {}", variant.id, failure);
      return System.nanoTime() - start;
    }
    long nanos = System.nanoTime() - start;
    DONE.put(key, Boolean.TRUE);
    warmNanos += nanos;
    warmCount++;
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[BogiePrewarm] style={} ms={} warmed={} totalMs={}",
            variant.id,
            nanos / 1e6,
            warmCount,
            warmNanos / 1e6);
    return nanos;
  }

  private BogiePrewarm() {}
}
