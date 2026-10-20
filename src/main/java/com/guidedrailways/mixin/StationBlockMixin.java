package com.guidedrailways.mixin;

import com.guidedrailways.StationPresence;
import com.simibubi.create.content.trains.station.StationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StationBlock.class, remap = false)
public abstract class StationBlockMixin {
  @Inject(method = "getAnalogOutputSignal", at = @At("RETURN"), cancellable = true, require = 1)
  private void guidedrailways$physicalComparator(
      BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
    if (StationPresence.present(level, pos))
      cir.setReturnValue(Math.max(cir.getReturnValueI(), 15));
  }
}
