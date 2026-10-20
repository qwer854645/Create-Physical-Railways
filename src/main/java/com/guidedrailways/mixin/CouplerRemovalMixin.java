package com.guidedrailways.mixin;

import com.guidedrailways.CouplerEndpoint;
import com.guidedrailways.RailwayHardware;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe replacement before IBE invalidation removes the endpoint from the live registry. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CouplerRemovalMixin {
  @Inject(method = "onRemove", at = @At("HEAD"))
  private void guidedrailways$remove(
      Level level, BlockPos pos, BlockState next, boolean moving, CallbackInfo ci) {
    if (!(level instanceof ServerLevel)) return;
    var old = (BlockState) (Object) this;
    if (old.is(next.getBlock()) && (!RailwayHardware.coupler(old) || RailwayHardware.coupler(next)))
      return;
    var endpoint = CouplerEndpoint.get(level.getBlockEntity(pos));
    if (endpoint != null) endpoint.destroyed();
  }
}
