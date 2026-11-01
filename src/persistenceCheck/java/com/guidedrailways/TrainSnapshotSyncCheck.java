package com.guidedrailways;

import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;

public final class TrainSnapshotSyncCheck {
  private static int checks;
  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
  private static byte[] bytes(CompoundTag tag) throws Exception {
    var output = new ByteArrayOutputStream();
    NbtIo.write(tag, new DataOutputStream(output));
    return output.toByteArray();
  }
  private static CompoundTag wire(CompoundTag tag) throws Exception {
    return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes(tag))));
  }
  private static CompoundTag bare(CompoundTag tag) {
    var copy = tag.copy();
    copy.remove("SyncRevision");
    return copy;
  }
  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    var state = new CompoundTag();
    state.putUUID("Id", UUID.randomUUID());
    state.putString("Dimension", "minecraft:overworld");
    var journey = new CompoundTag();
    journey.putString("Program", "unchanged schedule data ".repeat(300));
    journey.putIntArray("Path", new int[1000]);
    state.put("Journey", journey);
    var list = new ListTag();
    var carriage = new CompoundTag();
    carriage.putString("Model", "unchanged carriage geometry ".repeat(100));
    carriage.putDouble("X", 0);
    list.add(carriage);
    state.put("Carriages", list);
    var sync = new TrainSnapshotSync();
    var first = sync.next(state, 0, false);
    var client = TrainSnapshotSync.accept(null, wire(first));
    check(bare(client).equals(state), "initial complete baseline round-trips through binary NBT");
    check(sync.next(state, 10, false) == null, "idle train skips unchanged update");
    var login = sync.baseline();
    state.getList("Carriages", Tag.TAG_COMPOUND).getCompound(0).putDouble("X", 12.5);
    state.getCompound("Journey").putDouble("Remaining", 72.3);
    var delta = sync.next(state, 20, false);
    check(delta.getBoolean("SyncDelta"), "moving train uses incremental update");
    int fullBytes = bytes(state).length, deltaBytes = bytes(delta).length;
    check(deltaBytes < fullBytes / 4, "nested static program, path and carriage metadata are omitted");
    check(TrainSnapshotSync.accept(null, delta) == null, "missing baseline cannot create incomplete train");
    client = TrainSnapshotSync.accept(client, wire(delta));
    check(bare(client).equals(state), "compound and per-carriage changes reconstruct exact snapshot");
    check(bare(TrainSnapshotSync.accept(login, delta)).equals(state), "login baseline accepts next broadcast delta");
    check(TrainSnapshotSync.accept(client, delta) == null, "duplicate or mismatched delta is rejected");
    for (int i = 3; i < 80; i++) {
      if (i % 3 == 0) state.getCompound("Journey").remove("Remaining");
      else state.getCompound("Journey").putDouble("Remaining", i * 3.2);
      if (i % 7 == 0) state.put("Carriages", new ListTag());
      else state.put("Carriages", list.copy());
      if (i % 11 == 0) state.putString("Journey", "type replacement");
      else state.put("Journey", journey.copy());
      state.putInt("Progress", i);
      var packet = sync.next(state, i * 10L, false);
      client = TrainSnapshotSync.accept(client, wire(packet));
      check(bare(client).equals(state), "nested removals, list resizing and type changes: " + i);
    }
    var forced = sync.force(state);
    check(!forced.getBoolean("SyncDelta"), "station events force a complete broadcast");
    client = TrainSnapshotSync.accept(null, forced);
    check(bare(client).equals(state), "complete recovery works without previous state");
    check(sync.next(state, 1000, false) != null, "idle heartbeat repairs a missing baseline");
    var baseline = sync.baseline();
    baseline.remove("Journey");
    check(sync.baseline().contains("Journey"), "recipient cannot mutate publisher baseline");
    check(!state.contains("SyncRevision"), "network metadata never contaminates authoritative snapshot");
    String fallback = System.getProperty("guidedrailways.fullTrainSnapshots");
    try {
      System.setProperty("guidedrailways.fullTrainSnapshots", "true");
      check(!sync.next(state, 1010, false).getBoolean("SyncDelta"),
          "full-snapshot fallback works without losing revision continuity");
    } finally {
      if (fallback == null) System.clearProperty("guidedrailways.fullTrainSnapshots");
      else System.setProperty("guidedrailways.fullTrainSnapshots", fallback);
    }
    System.out.println("TrainSnapshotSyncCheck: " + checks + " checks; sample fullBytes="
        + fullBytes + " deltaBytes=" + deltaBytes);
  }
}
