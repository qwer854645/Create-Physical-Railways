package com.guidedrailways;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.tree.*;

/** Priority NBT round trips plus controller and lifecycle contracts; no world is started. */
public final class JunctionStateCheck {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = JunctionStateCheck.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node, 0);
    }
    return node;
  }

  private static boolean calls(String owner, String method, String target, String called) throws Exception {
    for (var m : code(owner).methods) {
      if (!m.name.equals(method)) continue;
      for (var insn : m.instructions)
        if (insn instanceof MethodInsnNode call
            && call.owner.equals("com/guidedrailways/" + target) && call.name.equals(called)) return true;
    }
    return false;
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    for (int value = 0; value <= SwitchQueue.MAX_PRIORITY; value++) {
      var saved = new CompoundTag();
      SchedulePriority.save(saved, value);
      var bytes = new java.io.ByteArrayOutputStream();
      net.minecraft.nbt.NbtIo.write(saved, new java.io.DataOutputStream(bytes));
      var restored = net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(
          new java.io.ByteArrayInputStream(bytes.toByteArray())));
      check(SchedulePriority.load(restored) == value, "saved priority round trip " + value);
    }
    var data = new CompoundTag();
    check(SchedulePriority.load(data) == 2, "old saves default to priority 2");
    for (int value : new int[] {-100, 100}) {
      data.putInt("Priority", value);
      check(SchedulePriority.load(data) == (value < 0 ? 0 : 5), "invalid saved priority clamped");
    }
    check(calls("ScheduleController", "save", "SchedulePriority", "save"), "controller saves priority");
    check(calls("ScheduleController", "load", "SchedulePriority", "load"), "controller restores priority");
    for (String cache : new String[] {"SchedulePriority", "SwitchBlockedReasons"}) {
      check(calls("TrainRuntime", "beforeTick", cache, "tick"), "world tick sweeps " + cache);
      check(calls("TrainRuntime", "unload", cache, "unload"), "world unload clears " + cache);
    }
    check(calls("TrainRuntime", "beforeTick", "RailwaysCompat", "tick"), "optional diagnostics are swept");
    check(calls("SteamRailways", "diagnose", "TickCache", "get"), "first diagnostic uses absent cache entry");
    check(calls("SwitchReservations", "report", "TickCache", "get"), "first queue log uses absent cache entry");
    check(calls("DriverMenu", "clickMenuButton", "ScheduleController", "action"), "menu routes schedule actions");
    // A waiting low-priority train loses to a higher-priority train, without stealing the live holder.
    var key = new SwitchReservations.Key(java.util.UUID.randomUUID(), 1);
    var holder = java.util.UUID.randomUUID();
    var low = java.util.UUID.randomUUID();
    var high = java.util.UUID.randomUUID();
    var claims = new SwitchReservations();
    check(claims.claim(key, holder, 1, 2, 100), "holder claims");
    check(!claims.claim(key, low, 1, 2, 110, 0), "low queues");
    check(!claims.claim(key, high, 1, 2, 111, 5), "high cannot steal live passage");
    check(!claims.claim(key, low, 1, 2, 141, 0), "low yields freed passage");
    check(claims.claim(key, high, 1, 2, 141, 5), "high takes freed passage");
    var stalled = new SwitchReservations();
    for (long tick = 200; tick <= 261; tick++) {
      stalled.claim(key, holder, 1, 2, tick);
      stalled.claim(key, low, 1, 2, tick);
    }
    var reported = SwitchReservations.class.getDeclaredField("reported");
    reported.setAccessible(true);
    check(((TickCache<?, ?>) reported.get(stalled)).size() == 1,
        "first starvation report is actually recorded after 60 ticks");
    System.out.println("JunctionStateCheck: controller NBT, legacy defaults, bounds, lifecycle wiring and priority passage passed");
  }
}
