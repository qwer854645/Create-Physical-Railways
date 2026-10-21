package com.guidedrailways;

import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** A local inventory view whose permission expires with the physical docking session. */
final class ScopedItemHandler implements IItemHandlerModifiable {
  private final IItemHandlerModifiable local;
  private final BooleanSupplier valid;

  ScopedItemHandler(IItemHandlerModifiable local, BooleanSupplier valid) {
    this.local = local;
    this.valid = valid;
  }

  private boolean slot(int slot) {
    return valid.getAsBoolean() && slot >= 0 && slot < local.getSlots();
  }

  public int getSlots() {
    return valid.getAsBoolean() ? local.getSlots() : 0;
  }

  public ItemStack getStackInSlot(int slot) {
    return slot(slot) ? local.getStackInSlot(slot).copy() : ItemStack.EMPTY;
  }

  public int getSlotLimit(int slot) {
    return slot(slot) ? local.getSlotLimit(slot) : 0;
  }

  public boolean isItemValid(int slot, ItemStack stack) {
    return slot(slot) && local.isItemValid(slot, stack);
  }

  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    return slot(slot) ? local.insertItem(slot, stack, simulate) : stack;
  }

  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    return slot(slot) ? local.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
  }

  public void setStackInSlot(int slot, ItemStack stack) {
    if (!slot(slot)) throw new IllegalStateException("Docking inventory access expired");
    local.setStackInSlot(slot, stack);
  }
}
