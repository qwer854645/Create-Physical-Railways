package com.physicalrailways;

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
  private static final class CountingCompound extends CompoundTag {
    int copies;
    CountingCompound(CompoundTag source) {
      for (var key : source.getAllKeys()) put(key, source.get(key).copy());
    }
    @Override public CompoundTag copy() { copies++; return super.copy(); }
  }

  private static void sizeMatches(CompoundTag tag) throws Exception {
    check(NbtWireSize.packet(tag) == bytes(tag).length, "size counter matches binary NBT encoding");
  }
  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    var state = new CompoundTag();
    state.putUUID("Id", new UUID(0, 0));
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
    var allTypes = new CompoundTag();
    allTypes.putByte("Byte", (byte) 1); allTypes.putShort("Short", (short) 2);
    allTypes.putInt("Int", 3); allTypes.putLong("Long", 4);
    allTypes.putFloat("Float", 1.5f); allTypes.putDouble("Double", -2.6);
    allTypes.putByteArray("Bytes", new byte[] {1, 2, 3});
    allTypes.putIntArray("Ints", new int[] {1, 2});
    allTypes.putLongArray("Longs", new long[] {1, 2});
    allTypes.putString("中文🚉\u0000", "ASCII 中文 🚉 \u0000 \u007f\u0080\u07ff\u0800");
    var primitiveList = new ListTag();
    primitiveList.add(IntTag.valueOf(4)); primitiveList.add(IntTag.valueOf(5));
    allTypes.put("List", primitiveList); allTypes.put("Empty", new ListTag());
    allTypes.put("Compound", state.copy());
    sizeMatches(allTypes);
    sizeMatches(new CompoundTag());
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
    var fullCandidate = state.copy();
    fullCandidate.putLong("SyncRevision", delta.getLong("SyncRevision"));
    int fullBytes = bytes(fullCandidate).length, deltaBytes = bytes(delta).length;
    check(deltaBytes < fullBytes / 4, "nested static program, path and carriage metadata are omitted");
    check(TrainSnapshotSync.accept(null, delta) == null, "missing baseline cannot create incomplete train");
    client = TrainSnapshotSync.accept(client, wire(delta));
    check(bare(client).equals(state), "compound and per-carriage changes reconstruct exact snapshot");
    check(bare(TrainSnapshotSync.accept(login, delta)).equals(state), "login baseline accepts next broadcast delta");
    check(TrainSnapshotSync.accept(client, delta) == null, "duplicate or mismatched delta is rejected");
    var countingBaseline = new CountingCompound(login);
    var received = TrainSnapshotSync.accept(countingBaseline, delta);
    check(countingBaseline.copies == 1, "incremental decode makes one private baseline copy");
    received.getCompound("Journey").putString("Program", "private mutation");
    check(!countingBaseline.getCompound("Journey").getString("Program").equals("private mutation"),
        "owned decode cannot mutate the previous baseline");
    var countedFull = new CountingCompound(first);
    var receivedFull = TrainSnapshotSync.accept(null, countedFull);
    check(countedFull.copies == 1, "complete decode makes one private packet copy");
    receivedFull.remove("Journey");
    check(countedFull.contains("Journey"), "owned full snapshot cannot mutate the network packet");
    var cachedBytes = TrainSnapshotSync.class.getDeclaredField("previousBytes");
    cachedBytes.setAccessible(true);
    for (int i = 3; i < 80; i++) {
      if (i % 3 == 0) state.getCompound("Journey").remove("Remaining");
      else state.getCompound("Journey").putDouble("Remaining", i * 3.2);
      if (i % 7 == 0) state.put("Carriages", new ListTag());
      else state.put("Carriages", list.copy());
      if (i % 11 == 0) state.putString("Journey", "type replacement");
      else state.put("Journey", journey.copy());
      state.putInt("Progress", i);
      var packet = sync.next(state, i * 10L, false);
      check(cachedBytes.getLong(sync) == bytes(state).length,
          "incremental full-size accounting remains exact after structural changes: " + i);
      var completeCandidate = state.copy();
      completeCandidate.putLong("SyncRevision", packet.getLong("SyncRevision"));
      check(bytes(packet).length <= bytes(completeCandidate).length,
          "selected packet never exceeds complete encoding: " + i);
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
    var small = new CompoundTag();
    small.putUUID("Id", new UUID(0, 0));
    small.putInt("Speed", 1);
    var smallSender = new TrainSnapshotSync();
    var smallBase = smallSender.next(small, 0, false);
    small.putInt("Speed", 2);
    var smallUpdate = smallSender.next(small, 10, false);
    check(!smallUpdate.getBoolean("SyncDelta"), "small updates choose the cheaper complete packet");
    check(bare(TrainSnapshotSync.accept(smallBase, smallUpdate)).equals(small),
        "automatic complete fallback preserves query data and revision continuity");
    var fleet = new ArrayList<TrainSnapshotSync>();
    var fleetStates = new ArrayList<CompoundTag>();
    for (int i = 0; i < 100; i++) {
      var sender = new TrainSnapshotSync();
      var train = new CompoundTag(); train.putUUID("Id", new UUID(0, i));
      sender.next(train, 0, false);
      fleet.add(sender); fleetStates.add(train);
    }
    int totalHeartbeats = 0, peakHeartbeats = 0;
    for (int tick = 10; tick <= 100; tick += 10) {
      int count = 0;
      for (int i = 0; i < fleet.size(); i++)
        if (fleet.get(i).next(fleetStates.get(i), tick, false) != null) count++;
      totalHeartbeats += count;
      peakHeartbeats = Math.max(peakHeartbeats, count);
    }
    check(totalHeartbeats == 100 && peakHeartbeats == 10,
        "simultaneously loaded fleet has one heartbeat each, spread across ten broadcast ticks");
    var rewound = fleet.get(0).next(fleetStates.get(0), 0, false);
    check(rewound != null && !rewound.getBoolean("SyncDelta"), "clock rewind forces a complete recovery baseline");
    String fallback = System.getProperty("physicalrailways.fullTrainSnapshots");
    try {
      System.setProperty("physicalrailways.fullTrainSnapshots", "true");
      check(!sync.next(state, 1010, false).getBoolean("SyncDelta"),
          "full-snapshot fallback works without losing revision continuity");
    } finally {
      if (fallback == null) System.clearProperty("physicalrailways.fullTrainSnapshots");
      else System.setProperty("physicalrailways.fullTrainSnapshots", fallback);
    }
    System.out.println("TrainSnapshotSyncCheck: " + checks + " checks; sample fullBytes="
        + fullBytes + " deltaBytes=" + deltaBytes + " heartbeatPeak=" + peakHeartbeats + "/100");
  }
}
