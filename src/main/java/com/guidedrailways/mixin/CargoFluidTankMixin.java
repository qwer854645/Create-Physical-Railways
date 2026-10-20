package com.guidedrailways.mixin;

import com.guidedrailways.CargoTransfers;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidTank.class, remap = false)
public abstract class CargoFluidTankMixin {
  @Inject(method = "setFluid", at = @At("HEAD"))
  private void guidedrailways$set(FluidStack stack, CallbackInfo ci) {
    if (CargoTransfers.tracked(this)
        && !FluidStack.matches(((FluidTank) (Object) this).getFluid(), stack))
      CargoTransfers.exchanged(this, false, 1);
  }

  @Inject(method = "fill", at = @At("RETURN"))
  private void guidedrailways$fill(
      FluidStack stack, FluidAction action, CallbackInfoReturnable<Integer> cir) {
    CargoTransfers.exchanged(this, action.simulate(), cir.getReturnValue());
  }

  @Inject(
      method =
          "drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;",
      at = @At("RETURN"))
  private void guidedrailways$drain(
      int amount, FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
    CargoTransfers.exchanged(this, action.simulate(), cir.getReturnValue().getAmount());
  }
}
