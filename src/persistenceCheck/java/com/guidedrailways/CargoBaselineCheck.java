package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;

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
    System.out.println(
        "CargoBaselineCheck: stale reads, idle reset, unchanged cargo, split, deletion and expiry passed");
  }
}
