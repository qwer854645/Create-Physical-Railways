package com.guidedrailways;

import java.util.*;
import java.util.concurrent.atomic.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.objectweb.asm.tree.*;

/**
 * Exercise expiring views without a game registry; inspect the real endpoint-selection boundary.
 */
public final class CargoBoundaryCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = CargoBoundaryCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  private static List<MethodInsnNode> calls(ClassNode code) {
    var result = new ArrayList<MethodInsnNode>();
    for (var method : code.methods)
      for (var instruction : method.instructions)
        if (instruction instanceof MethodInsnNode call) result.add(call);
    return result;
  }

  public static void main(String[] args) throws Exception {
    var active = new AtomicBoolean(true);
    var writes = new AtomicInteger();
    var source =
        new IItemHandlerModifiable() {
          public int getSlots() {
            return 1;
          }

          public int getSlotLimit(int slot) {
            return 64;
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return true;
          }

          public ItemStack getStackInSlot(int slot) {
            throw new AssertionError("no registry-backed stacks in this check");
          }

          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate) writes.incrementAndGet();
            return stack;
          }

          public ItemStack extractItem(int slot, int n, boolean simulate) {
            throw new AssertionError();
          }

          public void setStackInSlot(int slot, ItemStack stack) {
            writes.incrementAndGet();
          }
        };
    var handler = new ScopedItemHandler(source, active::get);
    check(
        handler.getSlots() == 1 && handler.getSlotLimit(0) == 64,
        "active view is exactly its local buffer");
    handler.setStackInSlot(0, null);
    check(writes.get() == 1, "active writes reach selected buffer");
    active.set(false);
    check(handler.getSlots() == 0 && handler.getSlotLimit(0) == 0, "expired view exposes no slots");
    check(!handler.isItemValid(0, null), "expired view cannot accept a parcel");
    for (boolean simulate : new boolean[] {true, false}) handler.insertItem(0, null, simulate);
    check(writes.get() == 1, "expired insertion cannot write even through retained reference");
    try {
      handler.setStackInSlot(0, null);
      throw new AssertionError("write accepted after expiry");
    } catch (IllegalStateException expected) {
      checks++;
    }
    active.set(true);
    check(
        handler.getSlots() == 1,
        "live permission is reevaluated without retaining an expired decision");
    var fluid =
        new ScopedFluidHandler(
            new IFluidHandler() {
              public int getTanks() {
                return 1;
              }

              public int getTankCapacity(int i) {
                return 8000;
              }

              public FluidStack getFluidInTank(int i) {
                throw new AssertionError();
              }

              public boolean isFluidValid(int i, FluidStack s) {
                return true;
              }

              public int fill(FluidStack s, FluidAction a) {
                writes.incrementAndGet();
                return 7;
              }

              public FluidStack drain(int n, FluidAction a) {
                throw new AssertionError();
              }

              public FluidStack drain(FluidStack s, FluidAction a) {
                throw new AssertionError();
              }
            },
            active::get);
    check(
        fluid.getTanks() == 1 && fluid.getTankCapacity(0) == 8000,
        "valid fluid view preserves capacity");
    active.set(false);
    check(fluid.getTanks() == 0 && fluid.getTankCapacity(0) == 0, "removed endpoint hides tanks");
    check(
        fluid.fill(null, IFluidHandler.FluidAction.EXECUTE) == 0 && writes.get() == 1,
        "stale fluid capability cannot fill");
    var lease = new RailDockLease();
    var owner = UUID.randomUUID();
    var other = UUID.randomUUID();
    lease.claim(owner, 10);
    check(
        lease.ownedBy(owner, 49) && !lease.ownedBy(other, 49) && !lease.ownedBy(owner, 50),
        "session checks owner and expiry");
    lease.release(owner, 20);
    check(!lease.ownedBy(owner, 20), "release revokes mailbox access immediately");
    var storage = code("com/guidedrailways/CargoStorage");
    check(
        storage.methods.stream().noneMatch(m -> m.name.equals("packageInventories")),
        "statistics do not expose a whole-train writable lookup");
    var logistics = code("com/guidedrailways/LogisticsCargoBridge");
    check(
        logistics.methods.stream().noneMatch(m -> m.name.equals("expand")),
        "selected CAAL containers are not expanded to coupled cars");
    var mail = calls(code("com/guidedrailways/PackageMail"));
    check(
        mail.stream().anyMatch(c -> c.owner.endsWith("RailDocks") && c.name.equals("mailBuffers")),
        "package exchange uses the actual dock boundary");
    var packageCode = code("com/guidedrailways/PackageMail");
    var outgoing =
        packageCode.methods.stream()
            .filter(m -> m.name.equals("outgoing"))
            .findFirst()
            .orElseThrow();
    var outboundCalls = new HashSet<String>();
    for (var i : outgoing.instructions)
      if (i instanceof MethodInsnNode call) outboundCalls.add(call.name);
    check(
        outboundCalls.containsAll(List.of("matchesAny", "matcher", "matches")),
        "outgoing mail respects all station addresses and the fetch filter");
    var pair = code("com/guidedrailways/RailDockContent");
    var gate =
        pair.methods.stream().filter(m -> m.name.equals("mailPair")).findFirst().orElseThrow();
    var invoked = new HashSet<String>();
    for (var i : gate.instructions) if (i instanceof MethodInsnNode call) invoked.add(call.name);
    for (var name :
        List.of(
            "ownedBy",
            "linkedStation",
            "getBlockEntity",
            "cargoComplete",
            "getOtherConnector",
            "ready")) check(invoked.contains(name), "mail access revalidates " + name);
    var duo =
        code(
            "dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorDuoInventory");
    for (var direction :
        Map.of("insertSlot", "theirInventory", "extractSlot", "ourInventory").entrySet()) {
      var method =
          duo.methods.stream()
              .filter(m -> m.name.equals(direction.getKey()))
              .findFirst()
              .orElseThrow();
      boolean usesExpectedBuffer = false;
      for (var i : method.instructions)
        if (i instanceof FieldInsnNode f && f.name.equals(direction.getValue()))
          usesExpectedBuffer = true;
      check(usesExpectedBuffer, "native dock direction preserved: " + direction.getKey());
    }
    System.out.println("CargoBoundaryCheck: " + checks + " checks passed");
  }
}
