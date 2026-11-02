package com.physicalrailways.mixin;

import com.physicalrailways.CargoTransfers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class CargoItemHandlerMixin {
  @Inject(method = "setStackInSlot", at = @At("HEAD"))
  private void physicalrailways$set(int slot, ItemStack stack, CallbackInfo ci) {
    if (CargoTransfers.tracked(this)
        && !ItemStack.matches(((ItemStackHandler) (Object) this).getStackInSlot(slot), stack))
      CargoTransfers.exchanged(this, false, 1);
  }

  @Inject(method = "insertItem", at = @At("RETURN"))
  private void physicalrailways$insert(
      int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
    CargoTransfers.exchanged(this, simulate, stack.getCount() - cir.getReturnValue().getCount());
  }

  @Inject(method = "extractItem", at = @At("RETURN"))
  private void physicalrailways$extract(
      int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
    CargoTransfers.exchanged(this, simulate, cir.getReturnValue().getCount());
  }
}
