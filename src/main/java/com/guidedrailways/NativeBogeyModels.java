package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Installed Create renderer API, independent of the optional mod owning the style. */
final class NativeBogeyModels {
  static boolean render(
      BogeyVariant variant,
      CompoundTag data,
      float angle,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var style = AllBogeyStyles.BOGEY_STYLES.get(ResourceLocation.parse(variant.id));
    var size = BogeySizes.all().get(ResourceLocation.parse(variant.size));
    if (style == null || size == null) return false;
    style.render(size, partial, pose, buffers, light, overlay, angle, data, false);
    return true;
  }

  private NativeBogeyModels() {}
}
