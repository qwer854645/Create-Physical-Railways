package com.guidedrailways;

public final class CargoClockCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var clock = new CargoClock();
    clock.exchanged(20, true, 64);
    clock.exchanged(21, false, 0);
    check(
        clock.version() == 0, "capacity simulation and blocked transfers cannot count as activity");
    check(!clock.idle(100, 0, 100), "native idle condition requires strictly longer than duration");
    check(clock.idle(101, 0, 100), "unchanged monitored storage eventually becomes idle");
    clock.exchanged(105, false, 64);
    clock.exchanged(105, false, 64);
    check(clock.version() == 2, "same-tick remove and refill must both record successful exchange");
    check(
        !clock.idle(200, 0, 100) && clock.idle(206, 0, 100), "net-zero exchange delays departure");
    check(
        !clock.idle(206, 200, 100), "a newly active condition starts its own observation interval");
    var loaded = new CargoClock();
    check(!loaded.idle(300, 300, 100), "reload does not restore unobserved idle time");
    check(
        CargoClock.itemAmount(63, 64, true) == 0 && CargoClock.itemAmount(64, 64, true) == 1,
        "Create stacks mode counts full slots, not fractional stacks");
    check(
        CargoClock.itemAmount(16, 16, true) == 1 && CargoClock.itemAmount(1, 1, true) == 1,
        "different item maximum stack sizes");
    check(CargoClock.itemAmount(63, 64, false) == 63, "items mode counts partial stacks");
    check(
        CargoClock.compare(1500, 1000, "GREATER"),
        "fluid comparison uses millibuckets before display rounding");
    check(
        !CargoClock.compare(1500, 1000, "EQUAL"),
        "one and a half buckets is not exactly one bucket");
    check(
        CargoClock.compare(3_000_000_000L, 2_147_483_647L, "GREATER"),
        "large inventories cannot wrap to negative counts");
    for (String op : new String[] {"GREATER", "LESS", "EQUAL"}) {
      check(
          CargoClock.compare(64, 64, op) == op.equals("EQUAL"),
          "strict native comparison at equality");
    }
    System.out.println("Cargo activity and threshold checks passed");
  }
}
