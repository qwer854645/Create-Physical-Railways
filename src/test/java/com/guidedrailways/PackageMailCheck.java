package com.guidedrailways;

import java.util.*;

public final class PackageMailCheck {
  private static int checks;

  private static void check(boolean value, String why) {
    checks++;
    if (!value) throw new AssertionError(why);
  }

  private record Parcel(int id, String address) {}

  private static final class Inventory implements ParcelTransfer.Slots<Parcel> {
    final Parcel[] slots;
    boolean take = true, put = true, failWrite;

    Inventory(int size) {
      slots = new Parcel[size];
    }

    public int size() {
      return slots.length;
    }

    public Parcel get(int s) {
      return slots[s];
    }

    public boolean canTake(int s, Parcel p) {
      return take;
    }

    public boolean accepts(int s, Parcel p) {
      return put;
    }

    public void set(int s, Parcel p) {
      slots[s] = p;
      if (failWrite) {
        failWrite = false;
        throw new IllegalStateException("injected write failure");
      }
    }
  }

  private static Set<Integer> parcels(Inventory... stores) {
    var ids = new HashSet<Integer>();
    int n = 0;
    for (var store : stores)
      for (var parcel : store.slots)
        if (parcel != null) {
          n++;
          ids.add(parcel.id);
        }
    check(n == ids.size(), "every parcel remains unique after exchange");
    return ids;
  }

  public static void main(String[] args) {
    var target = Set.of(UUID.randomUUID());
    check(
        PackageTask.decide(false, true, false, Set.of()) == PackageTask.Decision.SKIP,
        "no outbound mail skips retrieval");
    check(
        PackageTask.decide(true, true, false, Set.of()) == PackageTask.Decision.SKIP,
        "empty train skips delivery");
    check(
        PackageTask.decide(true, true, true, Set.of()) == PackageTask.Decision.NO_TARGET,
        "undeliverable parcels must not be silently skipped");
    check(
        PackageTask.decide(false, false, false, Set.of()) == PackageTask.Decision.UNLOADED,
        "unknown postbox contents are not empty");
    check(
        PackageTask.decide(true, false, false, Set.of()) == PackageTask.Decision.UNLOADED,
        "unknown carriage contents are not empty");
    check(
        PackageTask.decide(false, false, false, target) == PackageTask.Decision.VISIT,
        "known pickup can be visited despite another unloaded postbox");
    var car = new Inventory(3);
    var trailer = new Inventory(2);
    var box = new Inventory(3);
    car.slots[0] = new Parcel(1, "A");
    car.slots[1] = new Parcel(2, "B");
    trailer.slots[0] = new Parcel(3, "A");
    box.slots[0] = new Parcel(4, "B");
    var original = parcels(car, trailer, box);
    int delivered = ParcelTransfer.move(car, List.of(box), p -> p.address.equals("A"), 64);
    delivered +=
        ParcelTransfer.move(trailer, List.of(box), p -> p.address.equals("A"), 64 - delivered);
    check(delivered == 2, "delivery includes parcels from the trailer");
    check(car.slots[1] != null && car.slots[1].id == 2, "mail for other stations stays aboard");
    check(
        ParcelTransfer.move(box, List.of(car, trailer), p -> !p.address.equals("A"), 64) == 1,
        "only outgoing mail loads onto train");
    check(
        parcels(car, trailer, box).equals(original),
        "two-way transfer conserves exact parcel identities");
    check(
        ParcelTransfer.move(box, List.of(car, trailer), p -> !p.address.equals("A"), 64) == 0,
        "repeated stop tick cannot reload delivered mail");
    var full = new Inventory(1);
    full.slots[0] = new Parcel(5, "A");
    check(
        ParcelTransfer.move(car, List.of(full), p -> true, 64) == 0,
        "full destination leaves source untouched");
    var locked = new Inventory(1);
    locked.put = false;
    check(
        ParcelTransfer.move(car, List.of(locked), p -> true, 64) == 0,
        "insertion restrictions are preserved");
    car.take = false;
    check(
        ParcelTransfer.move(car, List.of(trailer), p -> true, 64) == 0,
        "extraction restrictions are preserved");
    car.take = true;
    var empty = new Inventory(3);
    check(
        ParcelTransfer.move(car, List.of(car), p -> true, 64) == 0,
        "inventory cannot send to itself");
    check(
        ParcelTransfer.move(car, List.of(empty), p -> true, 1) == 1,
        "per-stop transfer budget bounds work");
    check(
        ParcelTransfer.move(car, List.of(empty), p -> true, 0) == 0,
        "exhausted budget cannot mutate storage");
    check(
        parcels(car, trailer, box, empty).equals(original),
        "blocked attempts and budget retain all parcels");
    var source = new Inventory(1);
    var dest = new Inventory(1);
    source.slots[0] = new Parcel(99, "A");
    for (boolean sourceFails : new boolean[] {false, true}) {
      if (sourceFails) source.failWrite = true;
      else dest.failWrite = true;
      boolean failed = false;
      try {
        ParcelTransfer.move(source, List.of(dest), p -> true, 1);
      } catch (IllegalStateException expected) {
        failed = true;
      }
      check(
          failed && source.slots[0] != null && source.slots[0].id == 99 && dest.slots[0] == null,
          "a failed endpoint write restores both slots without duplicates or loss");
    }
    System.out.println("PackageMailCheck: " + checks + " assertions passed");
  }
}
