package com.guidedrailways;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.objectweb.asm.tree.*;

/** Placement storage and real native owner lookup contracts; no game world is started. */
public final class TrainOwnerCodecCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = TrainOwnerCodecCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static MethodNode method(ClassNode node, String name) {
    return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
  }

  private static List<MethodInsnNode> calls(MethodNode node) {
    var out = new ArrayList<MethodInsnNode>();
    for (var i : node.instructions) if (i instanceof MethodInsnNode c) out.add(c);
    return out;
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    var id = UUID.randomUUID();
    for (var owner :
        List.of(
            TrainOwner.UNKNOWN,
            new TrainOwner(id, "Player"),
            new TrainOwner(id, ""),
            new TrainOwner(id, "\u73a9\u5bb6"))) {
      var state = new DriverNetwork.MenuState(9, "Station", 350, "Route", 100, "Express", owner);
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        DriverNetwork.MenuState.CODEC.encode(buffer, state);
        check(
            state.equals(DriverNetwork.MenuState.CODEC.decode(buffer)),
            "menu preserves recorded or unknown owner");
        check(buffer.readableBytes() == 0, "owner fields do not corrupt other menu fields");
      } finally {
        buffer.release();
      }
      for (var prefix : List.of("PlacedBy", "Owner")) {
        var tag = new CompoundTag();
        if (owner.known()) {
          tag.putUUID(prefix, owner.id());
          tag.putString(prefix + "Name", owner.name());
        }
        var bytes = new java.io.ByteArrayOutputStream();
        NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        var data =
            NbtIo.read(
                new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        var restored =
            new TrainOwner(
                data.hasUUID(prefix) ? data.getUUID(prefix) : null,
                data.getString(prefix + "Name"));
        check(restored.equals(owner), "binary provenance round trip: " + prefix);
      }
    }
    var entity = code("com/guidedrailways/DriverConsoleEntity");
    var placed = method(entity, "placed");
    check(
        calls(placed).stream().anyMatch(c -> c.name.equals("getUUID"))
            && calls(placed).stream().anyMatch(c -> c.name.equals("getGameProfile")),
        "placement records actual placing player");
    for (var method : List.of("command", "configurationAllowed", "canConfigure", "setTrainName")) {
      check(
          java.util.stream.StreamSupport.stream(
                  method(entity, method).instructions.spliterator(), false)
              .noneMatch(i -> i instanceof FieldInsnNode f && f.name.equals("trainOwner")),
          "ownership does not restrict or change while driving or renaming: " + method);
    }
    var proxy = code("com/guidedrailways/PhysicalTrainProxy");
    check(
        java.util.stream.StreamSupport.stream(
                method(proxy, "apply").instructions.spliterator(), false)
            .anyMatch(
                i ->
                    i instanceof FieldInsnNode f
                        && f.name.equals("owner")
                        && f.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD),
        "snapshot writes actual native Train.owner field");
    var nativeTrain = code("com/simibubi/create/content/trains/entity/Train");
    check(
        calls(method(nativeTrain, "getOwner")).stream().anyMatch(c -> c.name.equals("getPlayer")),
        "installed native lookup resolves UUID through server player list");
    for (var name : List.of("saveAdditional", "loadAdditional")) {
      check(
          java.util.stream.StreamSupport.stream(
                  method(entity, name).instructions.spliterator(), false)
              .anyMatch(i -> i instanceof LdcInsnNode c && c.cst.equals("PlacedBy")),
          "console provenance persistence: " + name);
    }
    System.out.println(
        "TrainOwnerCodecCheck: " + checks + " packet, NBT and native-owner contract checks passed");
  }
}
