package com.guidedrailways.mixin;

import com.guidedrailways.RailDocks;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorSoloInventory;
import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Check only cross-endpoint movement; local buffers may still stage outgoing mail. */
@Pseudo
@Mixin(
    targets =
        "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorDuoInventory",
    remap = false)
public abstract class RailDockMailMixin {
  @Shadow @Final private DockingConnectorSoloInventory ourInventory;
  @Shadow @Final private DockingConnectorSoloInventory theirInventory;

  @Inject(method = "insertSlot", at = @At("HEAD"), cancellable = true)
  private void guidedrailways$slot(
      ItemStack stack, int slot, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
    if (!RailDocks.allowsBuffers(ourInventory, theirInventory, stack)) cir.setReturnValue(stack);
  }

  @Inject(method = "insertGeneral", at = @At("HEAD"), cancellable = true)
  private void guidedrailways$general(
      ItemInfoWrapper item, int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
    if (!RailDocks.allowsBuffers(
        ourInventory, theirInventory, ItemInfoWrapper.generateFromInfo(item)))
      cir.setReturnValue(0);
  }
}
