package com.guidedrailways;

import net.minecraft.nbt.*;

/** Real NBT vertical-layout conversion, including data preservation and refused truncation. */
public final class TransferSectionsCheck {
  private static int checks;
  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
  private static CompoundTag plot(int sectionIndex, String block) {
    var entry = new CompoundTag(); entry.putString("Name", block);
    var palette = new ListTag(); palette.add(entry);
    var states = new CompoundTag(); states.put("palette", palette);
    var section = new CompoundTag(); section.put("block_states", states);
    var sections = new CompoundTag(); sections.put(Integer.toString(sectionIndex), section);
    var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); item.putInt("count", 17);
    var items = new ListTag(); items.add(item);
    var chest = new CompoundTag(); chest.put("Items", items); chest.putString("CustomName", "Cargo");
    var entities = new ListTag(); entities.add(chest);
    var chunk = new CompoundTag(); chunk.put("sections", sections); chunk.put("block_entities", entities);
    chunk.put("heightmaps", new CompoundTag()); chunk.putBoolean("isLightOn", true);
    var chunks = new CompoundTag(); chunks.put("0", chunk);
    var plot = new CompoundTag(); plot.put("chunks", chunks); plot.putInt("plot_x", 3); plot.putInt("plot_z", 9);
    return plot;
  }
  public static void main(String[] args) {
    for (int y = 4; y < 20; y++) {
      var original = plot(y, "minecraft:chest");
      var copy = original.copy();
      var inventory = original.getCompound("chunks").getCompound("0").getList("block_entities", Tag.TAG_COMPOUND).copy();
      TransferSections.shift(copy, -4, 16);
      var chunk = copy.getCompound("chunks").getCompound("0");
      check(chunk.getCompound("sections").contains(Integer.toString(y - 4)), "Overworld to Nether keeps absolute Y");
      check(chunk.getList("block_entities", Tag.TAG_COMPOUND).equals(inventory), "inventory and custom name are unchanged");
      check(copy.getInt("plot_x") == 3 && copy.getInt("plot_z") == 9, "internal X/Z address is preserved");
      check(!chunk.contains("heightmaps") && !chunk.getBoolean("isLightOn"), "world-specific caches are invalidated");
      TransferSections.shift(copy, 4, 24);
      check(chunk.getCompound("sections").equals(original.getCompound("chunks").getCompound("0").getCompound("sections")),
          "round trip restores section addresses");
    }
    for (int y : new int[] {0, 3, 20, 23}) {
      boolean refused = false;
      try { TransferSections.shift(plot(y, "minecraft:chest"), -4, 16); }
      catch (IllegalArgumentException expected) { refused = true; }
      check(refused, "solid contents outside destination height cannot be silently dropped");
    }
    var air = plot(0, "minecraft:air");
    TransferSections.shift(air, -4, 16);
    check(air.getCompound("chunks").getCompound("0").getCompound("sections").isEmpty(), "outside empty section may be discarded");
    System.out.println("TransferSectionsCheck: " + checks + " vertical layout, cargo and round-trip checks passed");
  }
}
