package com.guidedrailways.mixin;

import com.guidedrailways.CargoStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes chunk teardown and replacement paths that do not produce player block events. */
@Mixin(BlockEntity.class)
public abstract class CargoEntityRemovalMixin {
  @Shadow
  public abstract Level getLevel();

  @Shadow
  public abstract BlockPos getBlockPos();

  @Inject(method = "setRemoved", at = @At("HEAD"))
  private void guidedrailways$removed(CallbackInfo ci) {
    CargoStorage.blockEntitiesChanged(getLevel(), getBlockPos());
  }
}
