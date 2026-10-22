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
          gate.tick(Set.of(a), t, true) == ScheduleReloadGate.Result.WAIT,
          "partial consist must wait");
    for (int t = 30; t < 39; t++)
      require(gate.tick(both, t, true) == ScheduleReloadGate.Result.WAIT, "settling period");
    require(gate.tick(both, 39, true) == ScheduleReloadGate.Result.READY, "restored consist");
    var missing = new ScheduleReloadGate(both);
    require(missing.tick(Set.of(a), 0, true) == ScheduleReloadGate.Result.WAIT, "missing member");
    require(
        missing.tick(Set.of(a), 20000, true) == ScheduleReloadGate.Result.WAIT,
        "absence alone is not a topology change");
    for (int t = 20001; t < 20010; t++)
      require(missing.tick(both, t, true) == ScheduleReloadGate.Result.WAIT, "late member settles");
    require(
        missing.tick(both, 20010, true) == ScheduleReloadGate.Result.READY, "late member resumes");
    require(!ScheduleReloadGate.missing(null, both), "first start can adopt a complete consist");
    require(
        ScheduleReloadGate.missing(both, Set.of(a)),
        "loaded subset cannot replace saved membership");
    require(
        !ScheduleReloadGate.missing(Set.of(a), both),
        "additional members remain a topology change");
    var extra = new ScheduleReloadGate(Set.of(a));
    require(extra.tick(both, 0, true) == ScheduleReloadGate.Result.WAIT, "extra member");
    require(
        extra.tick(both, 200, true) == ScheduleReloadGate.Result.CHANGED, "extra member timeout");
    var reset = new ScheduleReloadGate(both);
    for (int t = 0; t < 9; t++) reset.tick(both, t, true);
    reset.tick(Set.of(a), 9, true);
    require(
        reset.tick(both, 10, true) == ScheduleReloadGate.Result.WAIT,
        "interrupted stability resets");
    var mutable = new HashSet<>(Set.of(a));
    var copy = new ScheduleReloadGate(mutable);
    mutable.add(b);
    for (int t = 0; t < 9; t++)
      require(
          copy.tick(Set.of(a), t, true) == ScheduleReloadGate.Result.WAIT, "expected set copied");
    require(
        copy.tick(Set.of(a), 9, true) == ScheduleReloadGate.Result.READY,
        "copied membership ready");
    var legacy = new ScheduleReloadGate(Set.of());
    require(
        legacy.tick(Set.of(), 0, true) == ScheduleReloadGate.Result.WAIT, "empty cannot restore");
    for (int t = 1; t < 10; t++)
      require(
          legacy.tick(Set.of(a), t, true) == ScheduleReloadGate.Result.WAIT, "legacy stability");
    require(
        legacy.tick(Set.of(a), 10, true) == ScheduleReloadGate.Result.READY,
        "legacy stable consist");
    var delayed = new ScheduleReloadGate(both);
    for (int t = 0; t < 1000; t++)
      require(
          delayed.tick(both, t, false) == ScheduleReloadGate.Result.WAIT,
          "matching IDs cannot resume before saved couplings restore");
    for (int t = 1000; t < 1009; t++)
      require(
          delayed.tick(both, t, true) == ScheduleReloadGate.Result.WAIT, "ready train must settle");
    require(
        delayed.tick(both, 1009, true) == ScheduleReloadGate.Result.READY, "delayed train resumes");
    var interrupted = new ScheduleReloadGate(both);
    for (int t = 0; t < 9; t++) interrupted.tick(both, t, true);
    require(
        interrupted.tick(both, 9, false) == ScheduleReloadGate.Result.WAIT,
        "unload interrupts settling");
    for (int t = 5000; t < 5009; t++)
      require(
          interrupted.tick(both, t, true) == ScheduleReloadGate.Result.WAIT,
          "reloaded train settles anew");
    require(
        interrupted.tick(both, 5009, true) == ScheduleReloadGate.Result.READY,
        "reloaded train resumes");
    System.out.println("Schedule reload checks passed");
  }
}
