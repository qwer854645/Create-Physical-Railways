package com.guidedrailways;

import net.minecraft.nbt.*;

/** Keeps absolute internal Y coordinates when worlds have different minimum build heights. */
final class TransferSections {
  private TransferSections() {}

  static void shift(CompoundTag plot, int offset, int destinationSections) {
    var chunks = plot.getCompound("chunks");
    for (String key : chunks.getAllKeys()) {
      var chunk = chunks.getCompound(key);
      var before = chunk.getCompound("sections");
      var after = new CompoundTag();
      for (String index : before.getAllKeys()) {
        int target = Math.addExact(Integer.parseInt(index), offset);
        var section = before.getCompound(index);
        if (target < 0 || target >= destinationSections) {
          var palette = section.getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
          if (palette.isEmpty() || palette.stream().anyMatch(entry -> {
            String name = ((CompoundTag) entry).getString("Name");
            return !name.equals("minecraft:air") && !name.equals("minecraft:cave_air")
                && !name.equals("minecraft:void_air");
          })) throw new IllegalArgumentException("message.guidedrailways.transfer.height");
        } else after.put(Integer.toString(target), section.copy());
      }
      chunk.put("sections", after);
      // Height and light caches belong to the source world's vertical section layout.
      chunk.remove("heightmaps");
      chunk.putBoolean("isLightOn", false);
    }
  }
}
