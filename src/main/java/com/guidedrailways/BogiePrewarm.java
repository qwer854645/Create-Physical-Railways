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

  private static final Map<BogeyVariant, CompoundTag> PENDING = new IdentityHashMap<>();
  private static final Map<BogeyVariant, Long> REQUESTED_AT = new IdentityHashMap<>();
  private static final Map<BogeyVariant, Boolean> DONE = new IdentityHashMap<>();

  private static long warmNanos;
  private static int warmCount;
  private static long tick;

  static void request(BogeyVariant variant, CompoundTag data) {
    if (variant == null || variant == BogeyVariant.DEFAULT) return;
    if (DONE.containsKey(variant)) return;
    PENDING.putIfAbsent(variant, data == null ? new CompoundTag() : data.copy());
    REQUESTED_AT.putIfAbsent(variant, tick);
  }

  /** True when this style may be drawn without paying the first-draw cost. */
  static boolean warmed(BogeyVariant variant) {
    if (variant == BogeyVariant.DEFAULT) return true;
    if (DONE.containsKey(variant)) return true;
    var since = REQUESTED_AT.get(variant);
    return since != null && tick - since >= GIVE_UP_TICKS;
  }

  /** True while a request is queued, so the renderer can skip a single frame. */
  static boolean pending(BogeyVariant variant) {
    return variant != null && PENDING.containsKey(variant);
  }

  /** Bakes queued styles until the per-tick budget is spent. */
  static void tick() {
    tick++;
    if (PENDING.isEmpty()) return;
    // Snapshot the keys first: IdentityHashMap invalidates map entries as soon as they are removed,
    // so reading a key back out of an entry after removal throws.
    var queued = new java.util.ArrayList<>(PENDING.keySet());
    long spent = 0;
    for (var variant : queued) {
      if (spent >= BUDGET_NANOS) break;
      var data = PENDING.remove(variant);
      if (data == null) continue;
      spent += bake(variant, data);
    }
  }

  private static long bake(BogeyVariant variant, CompoundTag data) {
    long start = System.nanoTime();
    try {
      var pose = new PoseStack();
      pose.translate(.5, variant.renderY, .5);
      var byteBuffer = new ByteBufferBuilder(8192);
      try {
        var buffers = MultiBufferSource.immediate(byteBuffer);
        NativeBogeyModels.render(variant, data, 0, 0, pose, buffers, 0xF000F0, 0);
        // Submit once so the shader pipeline and vertex upload happen here, not on a visible frame.
        buffers.endBatch();
      } finally {
        byteBuffer.close();
      }
    } catch (RuntimeException failure) {
      DONE.put(variant, Boolean.FALSE);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("[BogiePrewarm] could not bake {}", variant.id, failure);
      return System.nanoTime() - start;
    }
    long nanos = System.nanoTime() - start;
    DONE.put(variant, Boolean.TRUE);
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
