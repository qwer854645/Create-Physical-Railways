package com.guidedrailways;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Native API safety contracts, without starting a game or creating a fake physical world. */
public final class NativeCargoCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = NativeCargoCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node, 0);
    }
    return node;
  }

  private static List<MethodInsnNode> calls(MethodNode method) {
    var out = new ArrayList<MethodInsnNode>();
    for (var instruction : method.instructions)
      if (instruction instanceof MethodInsnNode call) out.add(call);
    return out;
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    var manager = code("com/guidedrailways/PhysicalCargoManager");
    for (var name :
        List.of(
            "initialize",
            "resetIdleCargoTracker",
            "tickIdleCargoTracker",
            "attachExternal",
            "addBlock",
            "unmount",
            "write",
            "read",
            "tick",
            "handleSync",
            "handlePlayerStorageInteraction")) {
      var method =
          manager.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
      check(calls(method).isEmpty(), name + " cannot invoke native storage execution");
      for (var instruction : method.instructions)
        check(
            !(instruction instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD,
            name + " cannot rewrite mirrored inventory");
    }
    var unbind =
        manager.methods.stream().filter(m -> m.name.equals("unbind")).findFirst().orElseThrow();
    for (var field : List.of("level", "body", "members", "cachedGroup", "cached")) {
      boolean cleared = false;
      for (var instruction : unbind.instructions)
        if (instruction instanceof FieldInsnNode f
            && f.getOpcode() == Opcodes.PUTFIELD
            && f.name.equals(field)) cleared = true;
      check(cleared, "unbind releases " + field);
    }
    for (var kind : List.of("Items", "Fluids")) {
      var wrapper = code("com/guidedrailways/PhysicalCargoManager$" + kind);
      var writeNames =
          kind.equals("Items")
              ? List.of("setStackInSlot", "insertItem", "extractItem", "isItemValid")
              : List.of("fill", "drain", "isFluidValid");
      for (var method : wrapper.methods) {
        if (writeNames.contains(method.name)) {
          check(
              calls(method).isEmpty(),
              kind + "." + method.name + " cannot transfer or simulate a successful transfer");
          for (var instruction : method.instructions)
            check(
                !(instruction instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD,
                kind + "." + method.name + " cannot mutate source");
        }
        if (method.name.equals("getStackInSlot") || method.name.equals("getFluidInTank"))
          check(
              calls(method).stream().anyMatch(c -> c.name.equals("copy")),
              "query returns defensive stack copy");
      }
      // Keep the complete NeoForge handler surface overridden when dependency APIs change.
      String api =
          kind.equals("Items")
              ? "net/neoforged/neoforge/items/IItemHandler"
              : "net/neoforged/neoforge/fluids/capability/IFluidHandler";
      for (var method : code(api).methods)
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0)
          check(
              wrapper.methods.stream()
                  .anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
              "overrides native handler: " + method.name + method.desc);
    }
    // An unbound/client view must be safe to query and cannot retain a world reference.
    var cargo = new PhysicalCargoManager();
    check(!cargo.bound() && !cargo.ready(), "unbound view is unknown, not confirmed empty");
    check(
        cargo.getAllItems().getSlots() == 0 && cargo.getFluids().getTanks() == 0,
        "empty native query surfaces are initialized");
    check(
        cargo.getMountedItems().getSlots() == 0 && cargo.getFuelItems().getSlots() == 0,
        "no mounted storage or native fuel is manufactured");
    check(cargo.getAllItemStorages().isEmpty(), "no fake mounted container entries");
    int version = cargo.getVersion();
    cargo.unbind();
    check(
        cargo.getVersion() != version && cargo.getTicksSinceLastExchange() == 0,
        "unbind invalidates revision and does not claim idle");
    System.out.println("NativeCargoCheck: " + checks + " checks passed");
  }
}
