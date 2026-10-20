package com.guidedrailways;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Monotonic placement order shared across dimensions; physical assembly does not re-place a
 * console.
 */
final class ConsoleOrderData extends SavedData {
  private long next = 1;

  static long place(ServerLevel level) {
    var data =
        level
            .getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(
                new Factory<>(ConsoleOrderData::new, ConsoleOrderData::load, null),
                "guidedrailways_console_order");
    long order = data.next++;
    data.setDirty();
    return order;
  }

  private static ConsoleOrderData load(CompoundTag tag, HolderLookup.Provider registries) {
    var data = new ConsoleOrderData();
    data.next = Math.max(1, tag.getLong("Next"));
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putLong("Next", next);
    return tag;
  }
}
