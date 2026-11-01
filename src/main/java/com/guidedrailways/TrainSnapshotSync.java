package com.guidedrailways;

import java.util.Objects;
import net.minecraft.nbt.*;

/** Versioned query snapshots. Delta application always produces a complete private snapshot. */
final class TrainSnapshotSync {
  static final long HEARTBEAT_TICKS = 100;
  private CompoundTag previous;
  private long revision, fullAt, lastTick;
  private long previousBytes;
  private record Changes(CompoundTag patch, long bytes) {}

  private static boolean heartbeatDue(CompoundTag snapshot, long now, long last) {
    long phase = Math.floorMod(snapshot.getUUID("Id").hashCode(), 10) * 10L;
    return now < last || Math.floorDiv(now - phase, HEARTBEAT_TICKS)
        > Math.floorDiv(last - phase, HEARTBEAT_TICKS);
  }

  CompoundTag next(CompoundTag snapshot, long now, boolean force) {
    lastTick = now;
    boolean full = force || previous == null || heartbeatDue(snapshot, now, fullAt)
        || Boolean.getBoolean("guidedrailways.fullTrainSnapshots");
    if (!full && previous.equals(snapshot)) return null;
    long base = revision++;
    CompoundTag packet;
    if (full) {
      packet = snapshot.copy();
      previousBytes = NbtWireSize.packet(snapshot);
      fullAt = now;
    } else {
      packet = new CompoundTag();
      packet.putUUID("Id", snapshot.getUUID("Id"));
      packet.putBoolean("SyncDelta", true);
      packet.putLong("SyncBase", base);
      var changes = diff(previous, snapshot);
      previousBytes += changes.bytes;
      packet.put("Changes", changes.patch);
      packet.putLong("SyncRevision", revision);
      long fullBytes = previousBytes + NbtWireSize.named("SyncRevision", LongTag.valueOf(revision));
      if (NbtWireSize.packet(packet) >= fullBytes) {
        packet = snapshot.copy();
        fullAt = now;
      }
    }
    previous = snapshot.copy();
    packet.putLong("SyncRevision", revision);
    return packet;
  }

  CompoundTag force(CompoundTag snapshot) {
    return next(snapshot, lastTick, true);
  }

  /** Login uses the last broadcast baseline, not a newer unsent server snapshot. */
  CompoundTag baseline() {
    if (previous == null) return null;
    var packet = previous.copy();
    packet.putLong("SyncRevision", revision);
    return packet;
  }

  static CompoundTag accept(CompoundTag previous, CompoundTag packet) {
    if (!packet.getBoolean("SyncDelta")) return packet.copy();
    if (previous == null || !previous.hasUUID("Id")
        || !previous.getUUID("Id").equals(packet.getUUID("Id"))
        || previous.getLong("SyncRevision") != packet.getLong("SyncBase")
        || packet.getLong("SyncRevision") != packet.getLong("SyncBase") + 1) return null;
    var result = previous.copy();
    patch(result, packet.getCompound("Changes"));
    result.putLong("SyncRevision", packet.getLong("SyncRevision"));
    return result;
  }

  private static Changes diff(CompoundTag before, CompoundTag after) {
    var out = new CompoundTag();
    var set = new CompoundTag();
    var nested = new CompoundTag();
    var lists = new CompoundTag();
    var removed = new ListTag();
    long bytes = 0;
    for (var key : before.getAllKeys()) if (!after.contains(key)) {
      removed.add(StringTag.valueOf(key));
      bytes -= NbtWireSize.named(key, before.get(key));
    }
    for (var key : after.getAllKeys()) {
      var old = before.get(key);
      var value = after.get(key);
      if (Objects.equals(old, value)) continue;
      if (old instanceof CompoundTag a && value instanceof CompoundTag b) {
        var child = diff(a, b);
        nested.put(key, child.patch);
        bytes += child.bytes;
      }
      else if (old instanceof ListTag a && value instanceof ListTag b && a.size() == b.size()
          && a.getElementType() == Tag.TAG_COMPOUND && b.getElementType() == Tag.TAG_COMPOUND) {
        var changes = new CompoundTag();
        for (int i = 0; i < b.size(); i++)
          if (!a.get(i).equals(b.get(i))) {
            var child = diff(a.getCompound(i), b.getCompound(i));
            changes.put(Integer.toString(i), child.patch);
            bytes += child.bytes;
          }
        lists.put(key, changes);
      } else {
        set.put(key, value.copy());
        bytes += old == null ? NbtWireSize.named(key, value) : NbtWireSize.payload(value) - NbtWireSize.payload(old);
      }
    }
    out.put("Set", set);
    out.put("Nested", nested);
    out.put("Lists", lists);
    out.put("Removed", removed);
    return new Changes(out, bytes);
  }

  private static void patch(CompoundTag target, CompoundTag delta) {
    for (var key : delta.getList("Removed", Tag.TAG_STRING)) target.remove(key.getAsString());
    var set = delta.getCompound("Set");
    for (var key : set.getAllKeys()) target.put(key, set.get(key).copy());
    var nested = delta.getCompound("Nested");
    for (var key : nested.getAllKeys()) patch(target.getCompound(key), nested.getCompound(key));
    var lists = delta.getCompound("Lists");
    for (var key : lists.getAllKeys()) {
      var list = target.getList(key, Tag.TAG_COMPOUND);
      var changes = lists.getCompound(key);
      for (var index : changes.getAllKeys()) patch(list.getCompound(Integer.parseInt(index)), changes.getCompound(index));
    }
  }
}
