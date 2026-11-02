package com.physicalrailways;

import net.minecraft.nbt.CompoundTag;

/** Client display snapshot only; never restores navigation or driving state from disk. */
record ConsoleReadout(String target, int state, int entry, int total, int remaining, int speed) {
  static final ConsoleReadout EMPTY = new ConsoleReadout("", -1, 0, 0, -1, -1);

  boolean changedFrom(ConsoleReadout previous, ConsoleStatus previousReason, ConsoleStatus reason) {
    return !equals(previous) || previousReason != reason;
  }

  static ConsoleReadout of(String target, int state, int entry, int total,
      double remaining, double speed) {
    return new ConsoleReadout(target, state, Math.clamp(entry, 0, total), total,
        Double.isFinite(remaining) && remaining >= 0
            ? (int) Math.min(Integer.MAX_VALUE, Math.round(remaining * 10)) : -1,
        Double.isFinite(speed) ? (int) Math.min(Integer.MAX_VALUE, Math.round(Math.abs(speed) * 10)) : -1);
  }

  CompoundTag write() {
    var tag = new CompoundTag();
    tag.putString("Target", target);
    tag.putInt("State", state);
    tag.putInt("Entry", entry);
    tag.putInt("Total", total);
    tag.putInt("Remaining", remaining);
    tag.putInt("Speed", speed);
    return tag;
  }

  static ConsoleReadout read(CompoundTag tag) {
    if (!tag.contains("State")) return EMPTY;
    return new ConsoleReadout(tag.getString("Target"), tag.getInt("State"), tag.getInt("Entry"),
        tag.getInt("Total"), tag.getInt("Remaining"), tag.getInt("Speed"));
  }
}
