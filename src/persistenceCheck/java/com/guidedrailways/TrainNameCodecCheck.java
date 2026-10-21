package com.guidedrailways;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.objectweb.asm.tree.*;

public final class TrainNameCodecCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      codec.encode(buffer, value);
      check(
          value.equals(codec.decode(buffer)),
          "exact packet round trip: " + value.getClass().getSimpleName());
      check(buffer.readableBytes() == 0, "packet consumes all fields");
    } finally {
      buffer.release();
    }
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream =
        TrainNameCodecCheck.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static MethodNode method(ClassNode code, String name) {
    return code.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
  }

  private static List<MethodInsnNode> calls(MethodNode method) {
    var out = new ArrayList<MethodInsnNode>();
    for (var i : method.instructions) if (i instanceof MethodInsnNode c) out.add(c);
    return out;
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    for (var name :
        List.of("", "North Express", "\u7269\u7406\u94c1\u8def \ud83d\ude86", "x".repeat(64))) {
      roundTrip(DriverNetwork.SetName.CODEC, new DriverNetwork.SetName(8, name));
      roundTrip(DriverNetwork.NameResult.CODEC, new DriverNetwork.NameResult(8, true, name));
      roundTrip(DriverNetwork.NameResult.CODEC, new DriverNetwork.NameResult(8, false, name));
      roundTrip(
          DriverNetwork.MenuState.CODEC,
          new DriverNetwork.MenuState(8, "Target", 185, "Route", 75, name));
      var tag = new CompoundTag();
      tag.putString("TrainName", name);
      var bytes = new java.io.ByteArrayOutputStream();
      NbtIo.write(tag, new java.io.DataOutputStream(bytes));
      var restored =
          NbtIo.read(
              new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
      check(
          TrainName.normalize(restored.getString("TrainName")).equals(name),
          "console name survives binary NBT");
    }
    var entity = code("DriverConsoleEntity");
    check(
        calls(method(entity, "setTrainName")).stream().anyMatch(c -> c.name.equals("canConfigure")),
        "rename checks server-side configuration permission");
    check(
        calls(method(entity, "canConfigure")).stream().anyMatch(c -> c.name.equals("isPrimary")),
        "rename verifies current election, not just cached client view");
    for (var required : List.of("mayBuild", "mayInteract", "accessible"))
      check(
          calls(method(entity, "configurationAllowed")).stream()
              .anyMatch(c -> c.name.equals(required)),
          "configuration permission: " + required);
    check(
        calls(method(entity, "setTrainName")).stream()
            .noneMatch(c -> Set.of("park", "leave", "pause", "command", "manual").contains(c.name)),
        "rename cannot interrupt manual or automatic driving");
    for (var name : List.of("saveAdditional", "loadAdditional"))
      check(
          java.util.stream.StreamSupport.stream(
                  method(entity, name).instructions.spliterator(), false)
              .anyMatch(i -> i instanceof LdcInsnNode c && c.cst.equals("TrainName")),
          "console persistence key: " + name);
    check(
        calls(method(code("DriverMenu"), "setName")).stream()
            .anyMatch(c -> c.name.equals("stillValid")),
        "rename remains bound to accessible open menu");
    check(
        calls(method(code("PhysicalTrainProxy"), "apply")).stream()
            .anyMatch(c -> c.owner.endsWith("/TrainName") && c.name.equals("normalize")),
        "both native registries consume bounded plain names");
    System.out.println(
        "TrainNameCodecCheck: " + checks + " packet, NBT and permission contracts passed");
  }
}
