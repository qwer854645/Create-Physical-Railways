package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Real NBT relocation: keep cargo, relative chunk keys and tick delays; remap cross-body references. */
public final class TransferPlotsCheck {
  private static int checks;
  private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
  public static void main(String[] args) {
    var occupied = new BitSet(); occupied.set(0); occupied.set(3);
    check(TransferPlots.reserve(occupied, 3, 8) == 1, "occupied preferred slot finds a free plot");
    check(TransferPlots.reserve(occupied, 3, 8) == 2, "group reservation never reuses its own plot");
    check(TransferPlots.reserve(occupied, 6, 8) == 6, "retain a free preferred plot");
    occupied.set(0, 8);
    try { TransferPlots.reserve(occupied, 0, 8); throw new AssertionError("accepted full container"); }
    catch (IllegalArgumentException expected) { checks++; }
    for (int dx : new int[] {-7, 0, 9}) for (int dz : new int[] {-5, 0, 13}) {
      var move = new TransferPlots.Move(10, 20, 10 + dx, 20 + dz, 300, 500, 8);
      var peer = new TransferPlots.Move(12, 22, 4, 5, 300, 500, 8);
      var origin = new BlockPos(310 << 8, 128, 520 << 8);
      var peerPos = new BlockPos((312 << 8) + 11, 129, (522 << 8) + 9);
      var chest = new CompoundTag(); chest.putInt("x", origin.getX() + 9); chest.putInt("y", 128); chest.putInt("z", origin.getZ() + 7);
      chest.putLong("VisualPeerPos", peerPos.asLong()); chest.putString("CustomName", "Cargo");
      var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); item.putInt("count", 17);
      var items = new ListTag(); items.add(item); chest.put("Items", items);
      var be = new ListTag(); be.add(chest);
      var tank = new CompoundTag(); tank.putString("id", "create:fluid_tank");
      tank.putInt("x", origin.getX() + 4); tank.putInt("y", 129); tank.putInt("z", origin.getZ() + 4);
      tank.put("Controller", NbtUtils.writeBlockPos(origin)); tank.put("LastKnownPos", NbtUtils.writeBlockPos(origin.offset(4, 1, 4)));
      var fluid = new CompoundTag(); fluid.putString("FluidName", "minecraft:water"); fluid.putInt("Amount", 17000); tank.put("TankContent", fluid);
      be.add(tank);
      var tick = new CompoundTag(); tick.putInt("x", origin.getX()); tick.putInt("y", 130); tick.putInt("z", origin.getZ()); tick.putInt("t", 19);
      var ticks = new ListTag(); ticks.add(tick);
      var chunk = new CompoundTag(); chunk.put("block_entities", be); chunk.put("block_ticks", ticks); chunk.put("fluid_ticks", ticks.copy());
      var chunks = new CompoundTag(); chunks.put("4294967297", chunk);
      var plot = new CompoundTag(); plot.put("chunks", chunks);
      var body = new CompoundTag(); body.put("plot", plot);
      var beforeItems = items.copy();
      TransferPlots.apply(body, move, List.of(move, peer));
      check(chest.getInt("x") == origin.getX() + 9 + move.dx() && chest.getInt("z") == origin.getZ() + 7 + move.dz(), "BE moved");
      check(chest.getInt("y") == 128 && tick.getInt("y") == 130, "absolute Y remains stable between vanilla dimensions");
      check(chest.get("Items").equals(beforeItems) && chest.getString("CustomName").equals("Cargo"), "cargo and name preserved");
      check(chest.getLong("VisualPeerPos") == peer.point(peerPos).asLong(), "peer uses its own destination plot");
      check(tick.getInt("x") == origin.getX() + move.dx() && tick.getInt("t") == 19, "scheduled tick position only");
      check(chunks.contains("4294967297") && plot.getInt("plot_x") == move.newX(), "chunk keys remain relative");
      check(NbtUtils.readBlockPos(tank, "Controller").orElseThrow().equals(move.point(origin)), "tank controller follows its actual plot");
      check(NbtUtils.readBlockPos(tank, "LastKnownPos").orElseThrow().equals(move.point(origin.offset(4, 1, 4))), "tank does not mistake relocation for a multiblock split");
      check(tank.getCompound("TankContent").getInt("Amount") == 17000, "fluid is unchanged");
      check(!move.contains(origin.west()) && move.contains(origin), "plot boundaries are exact");
      var data = new CompoundTag(); var pos = new ListTag();
      pos.add(DoubleTag.valueOf(origin.getX() + .25)); pos.add(DoubleTag.valueOf(128.5)); pos.add(DoubleTag.valueOf(origin.getZ() + .75)); data.put("Pos", pos);
      TransferPlots.entity(data, move);
      check(pos.getDouble(0) == origin.getX() + .25 + move.dx() && pos.getDouble(1) == 128.5, "internal entity follows plot offset");
    }
    System.out.println("TransferPlotsCheck: " + checks + " assertions passed");
  }
}
