package com.guidedrailways.mixin;

import com.guidedrailways.CargoStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detect actual entity insertion, including packed NBT materialization and external replacements.
 */
@Mixin(LevelChunk.class)
public abstract class CargoEntityInsertionMixin {
  @Shadow
  public abstract Level getLevel();

  @Inject(method = "setBlockEntity", at = @At("TAIL"))
  private void guidedrailways$inserted(BlockEntity entity, CallbackInfo ci) {
    CargoStorage.blockEntitiesChanged(getLevel(), entity.getBlockPos());
  }
}
