package com.guidedrailways;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.objectweb.asm.tree.*;

/** Actual NBT/payload round trips and graph-availability rules, without starting a client. */
public final class ClientSignalCheck {
  private static int checks;

  private static void check(boolean value, String reason) {
    checks++;
    if (!value) throw new AssertionError(reason);
  }

  private static void invalid(CompoundTag tag) {
    try {
      SignalFrame.read(tag);
      throw new AssertionError("invalid frame accepted");
    } catch (IllegalArgumentException expected) {
      checks++;
    }
  }

  private static ClassNode code(String path) throws Exception {
    var node = new ClassNode();
    try (var stream = ClientSignalCheck.class.getResourceAsStream("/" + path + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var train = UUID.randomUUID();
    for (var frame :
        List.of(
            SignalFrame.UNKNOWN,
            new SignalFrame(1, true, Set.of(), Set.of()),
            new SignalFrame(2, true, Set.of(a), Set.of(b)),
            new SignalFrame(3, false, Set.of(a), Set.of(b)))) {
      check(frame.equals(SignalFrame.read(frame.write())), "known, empty and unknown round trip");
      var tag = new CompoundTag();
      tag.putUUID("Id", train);
      tag.putBoolean("SignalOnly", true);
      tag.putString("Dimension", "minecraft:overworld");
      tag.put("Signals", frame.write());
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        NativeTrainNetwork.Update.CODEC.encode(buffer, new NativeTrainNetwork.Update(tag));
        var result = NativeTrainNetwork.Update.CODEC.decode(buffer).data();
        check(
            result.equals(tag) && buffer.readableBytes() == 0,
            "actual network payload preserves identity and sections");
      } finally {
        buffer.release();
      }
    }
    var frame = new SignalFrame(4, true, Set.of(a), Set.of(b));
    check(
        !frame.resolvable(Set.of()) && !frame.resolvable(Set.of(a)),
        "partial graph never publishes a partial identity");
    check(
        frame.resolvable(Set.of(a, b)),
        "graph load can resolve retained frame without another packet");
    check(!frame.resolvable(Set.of(b)), "graph removal revokes previous ready state");
    check(
        !SignalFrame.UNKNOWN.resolvable(Set.of(a, b)), "unknown is different from confirmed empty");
    check(
        new SignalFrame(5, true, Set.of(), Set.of()).resolvable(Set.of()),
        "known empty graph needs no fake section");
    invalid(new CompoundTag());
    var broken = frame.write();
    broken.putLongArray("Occupied", new long[3]);
    invalid(broken);
    broken = frame.write();
    broken.putLong("Revision", -1);
    invalid(broken);
    broken = frame.write();
    broken.putLongArray("Claimed", new long[SignalFrame.LIMIT * 2 + 2]);
    invalid(broken);
    var source = new HashSet<>(Set.of(a));
    var frozen = new SignalFrame(9, true, source, Set.of());
    source.clear();
    check(frozen.occupied().equals(Set.of(a)), "frame owns immutable copies of section IDs");
    var state = code("com/guidedrailways/NativeSignalState");
    var publish =
        state.methods.stream().filter(m -> m.name.equals("publish")).findFirst().orElseThrow();
    for (var i : publish.instructions)
      if (i instanceof FieldInsnNode f)
        check(
            !f.owner.equals("com/simibubi/create/Create"),
            "client publication never falls back to the server railway manager");
    var client = code("com/guidedrailways/NativeTrainClient");
    var calls = new HashSet<String>();
    for (var method : client.methods)
      for (var i : method.instructions) if (i instanceof MethodInsnNode call) calls.add(call.name);
    check(
        calls.containsAll(List.of("retry", "clearJourney", "resetClient", "accept")),
        "client has delayed graph recovery and lifecycle cleanup");
    System.out.println("ClientSignalCheck: " + checks + " checks passed");
  }
}
