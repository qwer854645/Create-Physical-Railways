package com.guidedrailways;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Core code contains no optional Simulated classes in signatures or initializers. */
public final class RailDocks {
  interface Bridge {
    ItemStack item();

    int acquire(ServerLevel level, ScheduleController controller, CargoStorage.Snapshot cargo);

    void release(ServerLevel level, UUID console);

    boolean allowsHandoff(
        net.minecraft.world.level.block.entity.BlockEntity source,
        net.minecraft.world.level.block.entity.BlockEntity destination);

    boolean allowsBuffers(Object source, Object destination, ItemStack stack);
  }

  static Bridge bridge;

  static boolean available() {
    return bridge != null;
  }

  static boolean bound(ServerLevel level, UUID station) {
    return available() && !RailDockIndex.get(level).endpoints(station).isEmpty();
  }

  static int acquire(
      ServerLevel level, ScheduleController controller, CargoStorage.Snapshot cargo) {
    return bridge == null ? ScheduleController.RAIL_DOCK : bridge.acquire(level, controller, cargo);
  }

  static void release(ScheduleController controller) {
    if (bridge != null && controller.owner.getLevel() instanceof ServerLevel level)
      bridge.release(level, controller.owner.consoleId);
  }

  public static boolean allowsHandoff(
      net.minecraft.world.level.block.entity.BlockEntity source,
      net.minecraft.world.level.block.entity.BlockEntity destination) {
    return bridge == null || bridge.allowsHandoff(source, destination);
  }

  public static boolean allowsBuffers(Object source, Object destination, ItemStack stack) {
    return bridge == null || bridge.allowsBuffers(source, destination, stack);
  }
}
