package com.guidedrailways;

import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Reject retained capability references after their physical endpoint is replaced or unloaded. */
final class ScopedFluidHandler implements IFluidHandler {
  private final IFluidHandler local;
  private final BooleanSupplier valid;

  ScopedFluidHandler(IFluidHandler local, BooleanSupplier valid) {
    this.local = local;
    this.valid = valid;
  }

  private boolean tank(int tank) {
    return valid.getAsBoolean() && tank >= 0 && tank < local.getTanks();
  }

  public int getTanks() {
    return valid.getAsBoolean() ? local.getTanks() : 0;
  }

  public FluidStack getFluidInTank(int tank) {
    return tank(tank) ? local.getFluidInTank(tank).copy() : FluidStack.EMPTY;
  }

  public int getTankCapacity(int tank) {
    return tank(tank) ? local.getTankCapacity(tank) : 0;
  }

  public boolean isFluidValid(int tank, FluidStack stack) {
    return tank(tank) && local.isFluidValid(tank, stack);
  }

  public int fill(FluidStack stack, FluidAction action) {
    return valid.getAsBoolean() ? local.fill(stack, action) : 0;
  }

  public FluidStack drain(FluidStack stack, FluidAction action) {
    return valid.getAsBoolean() ? local.drain(stack, action) : FluidStack.EMPTY;
  }

  public FluidStack drain(int amount, FluidAction action) {
    return valid.getAsBoolean() ? local.drain(amount, action) : FluidStack.EMPTY;
  }
}
