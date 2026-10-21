package com.guidedrailways;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.tree.*;

/**
 * Persistent identity data and bytecode contracts; native object construction requires Minecraft
 * mod loading.
 */
public final class NativeTrainBridgeCheck {
  private static int checks;

  private static void check(boolean ok, String label) {
    checks++;
    if (!ok) throw new AssertionError(label);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = NativeTrainBridgeCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static void contract(String owner, String method) throws Exception {
    var node = code(owner);
    int hits = 0;
    for (var m : node.methods)
      if (m.name.equals(method))
        for (var instruction : m.instructions)
          if (instruction instanceof FieldInsnNode f
              && f.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD
              && f.owner.equals("com/simibubi/create/content/trains/GlobalRailwayManager")
              && f.name.equals("trains")) hits++;
    check(hits == 1, "one exact trains-map isolation site: " + method);
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    contract("com/simibubi/create/content/trains/GlobalRailwayManager", "playerLogin");
    contract("com/simibubi/create/content/trains/RailwaySavedData", "save");
    contract("com/simibubi/create/content/trains/entity/Train", "findCollidingTrain");
    var data = new TrainIdentityData();
    var members = Set.of(new UUID(1, 2), new UUID(3, 4));
    var id = data.ledger.reconcile(List.of(members)).get(members);
    var tag = data.save(new CompoundTag(), null);
    var restored = TrainIdentityData.load(tag, null);
    check(
        id.equals(restored.ledger.reconcile(List.of(members)).get(members)),
        "real NBT identity round trip");
    var node = code("com/guidedrailways/PhysicalTrainProxy");
    for (var methodName :
        List.of(
            "tick",
            "burnFuel",
            "crash",
            "approachTargetSpeed",
            "collectInitiallyOccupiedSignalBlocks",
            "detachFromTracks",
            "reattachToTracks",
            "arriveAt",
            "setCurrentStation",
            "leaveStation")) {
      var method =
          node.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
      var effects = new ArrayList<Integer>();
      for (var instruction : method.instructions)
        if (instruction.getOpcode() >= 0) effects.add(instruction.getOpcode());
      check(
          effects.equals(List.of(org.objectweb.asm.Opcodes.RETURN)),
          "native proxy operation has no side effects: " + methodName);
    }
    var carriage = code("com/guidedrailways/PhysicalTrainProxy$LogicalCarriage");
    var early =
        node.methods.stream().filter(m -> m.name.equals("earlyTick")).findFirst().orElseThrow();
    var earlyCalls = new ArrayList<MethodInsnNode>();
    for (var i : early.instructions) if (i instanceof MethodInsnNode call) earlyCalls.add(call);
    check(
        earlyCalls.size() == 1
            && earlyCalls.getFirst().owner.equals("com/guidedrailways/NativeSignalState")
            && earlyCalls.getFirst().name.equals("publish"),
        "early tick only republishes physical signal query state");
    var signal = code("com/guidedrailways/NativeSignalState");
    boolean removesExactTrain = false;
    for (var m : signal.methods)
      for (var i : m.instructions) {
        if (i instanceof FieldInsnNode f)
          check(
              !(f.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD
                  && f.owner.equals("com/simibubi/create/content/trains/signal/SignalEdgeGroup")
                  && f.name.equals("reserved")),
              "query projection never creates a native reservation");
        if (m.name.equals("clear")
            && i instanceof MethodInsnNode call
            && call.name.equals("remove")) removesExactTrain = true;
      }
    check(removesExactTrain, "cleanup removes its own train instead of clearing a shared group");
    var manage =
        carriage.methods.stream()
            .filter(m -> m.name.equals("manageEntities"))
            .findFirst()
            .orElseThrow();
    check(
        java.util.stream.StreamSupport.stream(manage.instructions.spliterator(), false)
            .noneMatch(i -> i instanceof MethodInsnNode),
        "logical carriage never creates entities");
    // Unit conversion is checked against the actual compiled native update instruction.
    var apply = node.methods.stream().filter(m -> m.name.equals("apply")).findFirst().orElseThrow();
    check(
        java.util.stream.StreamSupport.stream(apply.instructions.spliterator(), false)
            .anyMatch(
                i -> i instanceof LdcInsnNode constant && Double.valueOf(20).equals(constant.cst)),
        "proxy converts blocks/second into native blocks/tick");
    var binary = new java.io.ByteArrayOutputStream();
    net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(binary));
    var disk =
        net.minecraft.nbt.NbtIo.read(
            new java.io.DataInputStream(new java.io.ByteArrayInputStream(binary.toByteArray())));
    check(
        id.equals(
            TrainIdentityData.load(disk, null).ledger.reconcile(List.of(members)).get(members)),
        "binary NBT round trip");
    System.out.println(
        "NativeTrainBridgeCheck: "
            + checks
            + " isolation bytecode, binary NBT and upstream hook checks passed");
  }
}
