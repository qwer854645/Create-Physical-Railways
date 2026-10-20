package com.guidedrailways;

import java.util.*;

public final class ScheduleReloadCheck {
  private static void require(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var both = Set.of(a, b);
    var gate = new ScheduleReloadGate(both);
    for (int t = 0; t < 30; t++)
      require(
          gate.tick(Set.of(a), t) == ScheduleReloadGate.Result.WAIT, "partial consist must wait");
    for (int t = 30; t < 39; t++)
      require(gate.tick(both, t) == ScheduleReloadGate.Result.WAIT, "settling period");
    require(gate.tick(both, 39) == ScheduleReloadGate.Result.READY, "restored consist");
    var missing = new ScheduleReloadGate(both);
    require(missing.tick(Set.of(a), 0) == ScheduleReloadGate.Result.WAIT, "missing member");
    require(
        missing.tick(Set.of(a), 200) == ScheduleReloadGate.Result.CHANGED,
        "missing member timeout");
    var extra = new ScheduleReloadGate(Set.of(a));
    require(extra.tick(both, 0) == ScheduleReloadGate.Result.WAIT, "extra member");
    require(extra.tick(both, 200) == ScheduleReloadGate.Result.CHANGED, "extra member timeout");
    var reset = new ScheduleReloadGate(both);
    for (int t = 0; t < 9; t++) reset.tick(both, t);
    reset.tick(Set.of(a), 9);
    require(reset.tick(both, 10) == ScheduleReloadGate.Result.WAIT, "interrupted stability resets");
    var mutable = new HashSet<>(Set.of(a));
    var copy = new ScheduleReloadGate(mutable);
    mutable.add(b);
    for (int t = 0; t < 9; t++)
      require(copy.tick(Set.of(a), t) == ScheduleReloadGate.Result.WAIT, "expected set copied");
    require(copy.tick(Set.of(a), 9) == ScheduleReloadGate.Result.READY, "copied membership ready");
    var legacy = new ScheduleReloadGate(Set.of());
    require(legacy.tick(Set.of(), 0) == ScheduleReloadGate.Result.WAIT, "empty cannot restore");
    for (int t = 1; t < 10; t++)
      require(legacy.tick(Set.of(a), t) == ScheduleReloadGate.Result.WAIT, "legacy stability");
    require(legacy.tick(Set.of(a), 10) == ScheduleReloadGate.Result.READY, "legacy stable consist");
    System.out.println("Schedule reload checks passed");
  }
}
