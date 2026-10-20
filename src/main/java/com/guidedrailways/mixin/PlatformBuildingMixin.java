package com.guidedrailways.mixin;

import com.guidedrailways.PlatformBuilding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runs after Sable merges its unique helper into BlockPlaceContext, on both logical sides. */
@Mixin(value = BlockPlaceContext.class, priority = 1100)
public abstract class PlatformBuildingMixin {
  @Dynamic("Unique helper merged by Sable's BlockPlaceContextMixin")
  @ModifyExpressionValue(
      method = "sable$intersectBlocks",
      at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;lengthSquared()D"),
      remap = false,
      require = 1)
  private double guidedrailways$preciseMountSpace(
      double original,
      @Local(argsOnly = true) SubLevel other,
      @Local BlockPos cell,
      @Local(argsOnly = true) Vector3d candidateCentre,
      @Local(argsOnly = true) Quaterniond candidateRotation) {
    return PlatformBuilding.refine(
        original,
        (BlockPlaceContext) (Object) this,
        other,
        cell,
        candidateCentre,
        candidateRotation);
  }
}
