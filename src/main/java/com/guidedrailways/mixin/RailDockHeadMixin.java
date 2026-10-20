package com.guidedrailways.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Keep the native extension block attached to the railway subtype as well as the native base. */
@Pseudo
@Mixin(
    targets =
        "dev.simulated_team.simulated.content.blocks.docking_connector.PairedDockingConnectorBlock",
    remap = false)
public abstract class RailDockHeadMixin {
  @Redirect(
      method = {"updateShape", "playerWillDestroy"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/core/Holder;)Z"),
      require = 2)
  private boolean guidedrailways$railwayOwner(BlockState state, Holder<Block> nativeBlock) {
    return state.is(nativeBlock)
        || state
            .getBlock()
            .builtInRegistryHolder()
            .key()
            .location()
            .equals(
                ResourceLocation.fromNamespaceAndPath("guidedrailways", "rail_docking_connector"));
  }
}
