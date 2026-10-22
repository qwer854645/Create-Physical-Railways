package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Real snapshots and activity clocks, without constructing a Minecraft world. */
public final class CargoBaselineCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  private static CargoStorage.Snapshot snapshot(
      Set<UUID> members, boolean complete, boolean container, long tick) {
    return new CargoStorage.Snapshot(
        members,
        complete,
        true,
        List.of(),
        List.of(),
        container
            ? List.of(new CargoStorage.Location(new BlockPos(1, 64, 1), true, false, false))
            : List.of(),
        Map.of(),
        tick);
  }

  public static void main(String[] args) {
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var members = Set.of(a, b);
    var cache = new ConsistCache<UUID, CargoStorage.Snapshot>();
    var clock = new CargoClock();
    clock.exchanged(0, false, 1);
    var original = snapshot(members, true, true, 0);
    cache.put(members, original);
    var token = cache.token(a);
    cache.invalidate(Set.of(b));
    check(
        cache.get(a) == null && cache.get(b) == null,
        "dirty capture cannot be served to any carriage");
    check(
        cache.previous(a) == original && cache.previous(b) == original,
        "comparison baseline survives invalidation");
    check(
        cache.token(a) != token && cache.token(a) == cache.token(b),
        "all native views observe the same invalidation token");
    var removed = snapshot(members, true, false, 101);
    if (CargoStorage.contentsChanged(cache.previous(a), removed)) clock.exchanged(101, false, 1);
    cache.put(members, removed);
    check(
        clock.last() == 101 && !clock.idle(101, 0, 100),
        "removing a container resets the real idle clock");
    check(
        !clock.idle(201, 0, 100) && clock.idle(202, 0, 100),
        "departure requires a fresh full idle interval");
    cache.invalidate(Set.of(a));
    var unchanged = snapshot(members, true, false, 150);
    check(
        !CargoStorage.contentsChanged(cache.previous(a), unchanged),
        "unrelated entity changes do not count as cargo activity");
    check(
        !CargoStorage.contentsChanged(original, snapshot(members, false, false, 151)),
        "incomplete scans cannot invent cargo changes");
    check(
        !CargoStorage.contentsChanged(original, snapshot(Set.of(a), true, false, 151)),
        "different consists are not comparable");
    cache.put(Set.of(a), unchanged);
    check(cache.previous(b) == null, "split removes the old sibling baseline");
    cache.discard(Set.of(a));
    check(
        cache.previous(a) == null && cache.token(a) == null,
        "deletion releases both data and baseline");
    cache.put(members, original);
    cache.invalidate(Set.of(b));
    cache.removeIf(s -> s.captured() < 100);
    check(
        cache.previous(a) == null && cache.previous(b) == null,
        "expiry releases dirty captures too");
    var context = new CompoundTag();
    context.putInt("Progress", 2);
    var idleClock = new CargoClock();
    long since = ScheduleController.cargoWaitStart(context, 0);
    for (int tick = 1; tick <= 100; tick++)
      since = ScheduleController.cargoWaitStart(context, tick);
    check(since == 0 && !idleClock.idle(100, since, 100), "continuous idle keeps the start");
    // AutomaticTrains may skip all condition evaluation while couplings are incomplete.
    since = ScheduleController.cargoWaitStart(context, 1000);
    check(since == 1000 && !idleClock.idle(1000, since, 100), "unobserved time is not idle");
    for (int tick = 1001; tick <= 1101; tick++)
      since = ScheduleController.cargoWaitStart(context, tick);
    check(idleClock.idle(1101, since, 100), "fresh complete idle interval can depart");
    check(ScheduleController.cargoWaitStart(context, 1101) == 1000, "same-tick reads do not reset");
    idleClock.exchanged(1101, false, 1);
    check(!idleClock.idle(1101, since, 100), "successful exchange still resets idle");
    context.putLong("CargoNextCheck", 1200);
    ScheduleController.resetCargoObservation(context);
    check(
        !context.contains("CargoWaitStarted")
            && !context.contains("CargoLastObserved")
            && !context.contains("CargoNextCheck"),
        "load and invalid observation clear ephemeral clocks");
    check(context.getInt("Progress") == 2, "unrelated condition context is preserved");
    check(
        ScheduleController.cargoWaitStart(context, 5000) == 5000,
        "readback starts a fresh interval");
    check(
        ScheduleController.cargoWaitStart(context, 5) == 5,
        "clock rollback starts a fresh interval");
    System.out.println(
        "CargoBaselineCheck: stale reads, interrupted idle, readback reset, unchanged cargo, split, deletion and expiry passed");
  }
}
