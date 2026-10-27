package com.guidedrailways;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/**
 * Bakes a bogie model once, as soon as its block entity loads, instead of on the first visible
 * frame. Measured first draws of a new native style cost 14 to 53 ms, which is what dropped the
 * frame rate right after a style appeared on screen.
 */
final class BogiePrewarm {
  private static final Set<BogeyVariant> PENDING =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final Set<BogeyVariant> DONE =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static long warmNanos;
  private static int warmCount;

  static void request(BogeyVariant variant) {
    if (variant == null || variant == BogeyVariant.DEFAULT || DONE.contains(variant)) return;
    PENDING.add(variant);
  }

  /** One style per client tick: the bake lands while chunks are still arriving, not on a frame. */
  static void tick() {
    var iterator = PENDING.iterator();
    if (!iterator.hasNext()) return;
    var variant = iterator.next();
    iterator.remove();
    DONE.add(variant);
    long start = System.nanoTime();
    try {
      var pose = new PoseStack();
      pose.translate(.5, variant.renderY, .5);
      var byteBuffer = new ByteBufferBuilder(8192);
      try {
        var buffers = MultiBufferSource.immediate(byteBuffer);
        NativeBogeyModels.render(variant, new CompoundTag(), 0, 0, pose, buffers, 0xF000F0, 0);
        // Submit once so the shader pipeline and vertex upload happen here, not on a visible frame.
        buffers.endBatch();
      } finally {
        byteBuffer.close();
      }
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("[BogiePrewarm] could not bake {}", variant.id, failure);
      return;
    }
    long nanos = System.nanoTime() - start;
    warmNanos += nanos;
    warmCount++;
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[BogiePrewarm] style={} ms={} warmed={} totalMs={}",
            variant.id,
            nanos / 1e6,
            warmCount,
            warmNanos / 1e6);
  }

  private BogiePrewarm() {}
}
