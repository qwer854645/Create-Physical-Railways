package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Explicit relocation of Sable plot coordinates; arbitrary mod NBT is never guessed or rewritten. */
final class TransferPlots {
  record Move(int oldX, int oldZ, int newX, int newZ, int originX, int originZ, int bits) {
    int dx() { return (newX - oldX) << bits; }
    int dz() { return (newZ - oldZ) << bits; }
    boolean contains(BlockPos pos) {
      return (pos.getX() >> bits) == oldX + originX && (pos.getZ() >> bits) == oldZ + originZ;
    }
    BlockPos point(BlockPos pos) { return pos.offset(dx(), 0, dz()); }
    Vec3 point(Vec3 pos) { return pos.add(dx(), 0, dz()); }
  }

  static int reserve(BitSet used, int preferred, int limit) {
    int selected = preferred >= 0 && preferred < limit && !used.get(preferred) ? preferred : used.nextClearBit(0);
    if (selected >= limit) throw new IllegalArgumentException("message.guidedrailways.transfer.occupied");
    used.set(selected);
    return selected;
  }

  static BlockPos point(Collection<Move> moves, BlockPos position) {
    for (var move : moves) if (move.contains(position)) return move.point(position);
    throw new IllegalArgumentException("message.guidedrailways.transfer.incomplete");
  }

  static void apply(CompoundTag body, Move move, Collection<Move> all) {
    var plot = body.getCompound("plot");
    plot.putInt("plot_x", move.newX); plot.putInt("plot_z", move.newZ);
    // Chunk keys are plot-relative. Block entities and scheduled ticks contain absolute block positions.
    var chunks = plot.getCompound("chunks");
    for (String key : chunks.getAllKeys()) {
      var chunk = chunks.getCompound(key);
      for (var value : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
        var be = (CompoundTag) value;
        position(be, move);
        if (be.getString("id").equals("create:item_vault") || be.getString("id").equals("create:fluid_tank"))
          for (String field : List.of("Controller", "LastKnownPos")) if (be.contains(field)) {
            var old = NbtUtils.readBlockPos(be, field).orElseThrow(() -> new IllegalArgumentException("message.guidedrailways.transfer.incomplete"));
            be.put(field, NbtUtils.writeBlockPos(point(all, old)));
          }
        if (be.contains("VisualPeerPos", Tag.TAG_LONG))
          be.putLong("VisualPeerPos", point(all, BlockPos.of(be.getLong("VisualPeerPos"))).asLong());
      }
      for (String list : List.of("block_ticks", "fluid_ticks"))
        for (var value : chunk.getList(list, Tag.TAG_COMPOUND)) position((CompoundTag) value, move);
      chunk.remove("heightmaps"); chunk.putBoolean("isLightOn", false);
    }
  }

  private static void position(CompoundTag tag, Move move) {
    if (!tag.contains("x", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT))
      throw new IllegalArgumentException("message.guidedrailways.transfer.incomplete");
    tag.putInt("x", Math.addExact(tag.getInt("x"), move.dx()));
    tag.putInt("z", Math.addExact(tag.getInt("z"), move.dz()));
  }

  static void entity(CompoundTag tag, Move move) {
    var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
    if (pos.size() != 3) throw new IllegalArgumentException("message.guidedrailways.transfer.incomplete");
    pos.set(0, DoubleTag.valueOf(pos.getDouble(0) + move.dx()));
    pos.set(2, DoubleTag.valueOf(pos.getDouble(2) + move.dz()));
  }

  private TransferPlots() {}
}
