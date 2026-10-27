package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/** Installed Create renderer API, independent of the optional mod owning the style. */
final class NativeBogeyModels {
  private record Node(com.simibubi.create.content.trains.bogey.BogeyStyle style,
      BogeySizes.BogeySize size) {}

  private static final ConcurrentHashMap<BogeyVariant, Node> CACHE = new ConcurrentHashMap<>();

  static boolean render(
      BogeyVariant variant,
      CompoundTag data,
      float angle,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    // Resolved once per style instead of parsing two identifiers on every frame.
    var node =
        CACHE.computeIfAbsent(
            variant,
            key -> {
              var style =
                  AllBogeyStyles.BOGEY_STYLES.get(
                      net.minecraft.resources.ResourceLocation.parse(key.id));
              var size =
                  style == null
                      ? null
                      : BogeySizes.all().get(net.minecraft.resources.ResourceLocation.parse(key.size));
              return new Node(style, size);
            });
    if (node.style() == null || node.size() == null) return false;
    node.style().render(node.size(), partial, pose, buffers, light, overlay, angle, data, false);
    return true;
  }

  private NativeBogeyModels() {}
}