package com.guidedrailways.mixin;

import com.guidedrailways.CargoTransfers;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "dev.simulated_team.simulated.multiloader.inventory.SingleSlotContainer",
    remap = false)
public abstract class DockCargoItemMixin {
  @Inject(
      method = {"insertGeneral", "extractGeneral"},
      at = @At("RETURN"))
  private void guidedrailways$general(
      @Coerce Object item, int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
    CargoTransfers.exchanged(this, simulate, cir.getReturnValue());
  }

  @Inject(method = "insertSlot", at = @At("RETURN"))
  private void guidedrailways$insert(
      ItemStack stack, int slot, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
    CargoTransfers.exchanged(this, simulate, stack.getCount() - cir.getReturnValue().getCount());
  }

  @Inject(method = "extractSlot", at = @At("RETURN"))
  private void guidedrailways$extract(
      int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
    CargoTransfers.exchanged(this, simulate, cir.getReturnValue().getCount());
  }
}
