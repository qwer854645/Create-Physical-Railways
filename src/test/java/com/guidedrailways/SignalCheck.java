package com.guidedrailways;

import java.util.*;

public final class SignalCheck {
  private static int checks;

  private static UUID id(int n) {
    return new UUID(0, n);
  }

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    UUID a = id(1),
        b = id(2),
        block = id(10),
        next = id(11),
        crossing = id(12),
        signal = id(20),
        otherSignal = id(21);
    var ledger = new SignalOccupancy();
    ledger.refresh(Map.of(a, Set.of(block, next)), Set.of(a), 0);
    check(
        ledger.nativeBlocked(block, null),
        "physical parked and moving trains must obstruct native trains");
    check(
        !ledger.blocked(block, a) && ledger.blocked(block, b),
        "a consist excludes its own footprint, not another train");
    ledger.refresh(Map.of(a, Set.of(block, next)), Set.of(a), 1);
    check(
        ledger.nativeBlocked(block, signal),
        "a long train spanning a boundary holds the rear section after its head enters the next");
    ledger.refresh(Map.of(a, Set.of(next)), Set.of(a), 2);
    check(
        !ledger.nativeBlocked(block, null) && ledger.nativeBlocked(next, null),
        "release the old section only once the complete footprint has cleared it");
    ledger.refresh(Map.of(a, Set.of(next), b, Set.of(next)), Set.of(), 3);
    ledger.refresh(Map.of(b, Set.of(next)), Set.of(), 4);
    check(
        ledger.nativeBlocked(next, null),
        "one departing train cannot clear a section still containing another");
    ledger.refresh(Map.of(), Set.of(a, b), 5);
    check(
        ledger.claim(a, Map.of(block, signal, crossing, signal), 5, g -> false),
        "first train atomically claims its section and crossing conflict");
    check(
        !ledger.claim(b, Map.of(crossing, otherSignal, next, otherSignal), 5, g -> false),
        "simultaneous conflicting entry cannot be granted twice");
    check(
        !ledger.nativeBlocked(next, null),
        "failed multi-section claim leaves no partial reservation");
    check(
        ledger.nativeBlocked(block, null)
            && !ledger.nativeBlocked(block, signal)
            && ledger.nativeBlocked(block, otherSignal),
        "native trains stop for a claim while its own entry signal remains clear");
    check(ledger.claim(a, Map.of(block, signal), 6, g -> false), "owner can renew its claim");
    check(
        !ledger.claim(b, Map.of(next, otherSignal), 6, g -> g.equals(next)),
        "native occupancy and reservation reject a physical claim");
    ledger.refresh(Map.of(a, Set.of(block)), Set.of(a, b), 7);
    check(
        ledger.nativeBlocked(block, signal),
        "entering the claimed section replaces a green claim with real red occupation");
    ledger.refresh(Map.of(a, Set.of(block)), Set.of(a, b), 16);
    check(
        !ledger.nativeBlocked(crossing, null),
        "expired unoccupied claims cannot leave phantom blockage");
    check(
        ledger.claim(b, Map.of(next, otherSignal), 16, g -> false),
        "a clear section can be acquired after release");
    ledger.refresh(Map.of(a, Set.of(block)), Set.of(a), 17);
    check(
        !ledger.nativeBlocked(next, null) && ledger.nativeBlocked(block, null),
        "manual takeover or pause releases future claims but not physical occupation");
    ledger.refresh(Map.of(), Set.of(), 18);
    check(!ledger.nativeBlocked(block, null), "removing the physical footprint clears occupation");
    ledger.refresh(Map.of(a, Set.of(block, next), b, Set.of(crossing)), Set.of(a, b), 30);
    check(
        ledger.occupiedBy(a).equals(Set.of(block, next)),
        "projection preserves a long train's complete coverage");
    check(
        !ledger.blocked(block, a) && ledger.blocked(block, null),
        "native identity excludes only its own physical owner");
    ledger.refresh(Map.of(a, Set.of(block), b, Set.of(block)), Set.of(a, b), 31);
    check(ledger.blocked(block, a), "self exclusion cannot conceal a second physical occupant");
    ledger.refresh(Map.of(a, Set.of(block)), Set.of(a), 32);
    check(
        ledger.claim(a, Map.of(next, signal), 32, g -> false),
        "projection can observe an existing physical route claim");
    check(
        ledger.claimedBy(a).equals(Set.of(next)) && ledger.claimedBy(b).isEmpty(),
        "claims retain their actual owner");
    check(
        !ledger.blocked(next, a) && ledger.blocked(next, b),
        "own future claim is excluded without granting it to others");
    ledger.refresh(Map.of(b, Set.of(block)), Set.of(), 33);
    check(
        ledger.occupiedBy(a).isEmpty() && ledger.claimedBy(a).isEmpty(),
        "merge removal and pause release obsolete identity views");
    check(
        ledger.occupiedBy(b).equals(Set.of(block)), "another train's occupation survives cleanup");
    // Integrate the actual bounded service-force model at several speed and braking ratios.
    for (double cap : new double[] {3.2, 12.8, 25.6})
      for (double fraction : new double[] {.25, .5, 1}) {
        double braking = 4 * fraction, room = cap * cap / braking + cap * .3 + 8, speed = cap;
        double nearest = room;
        for (int step = 0; step < 20000; step++) {
          var demand = AutomaticDriveModel.demand(room, speed, cap, braking);
          double force =
              DriverModel.manualForce(
                  speed,
                  demand.ceiling(),
                  256,
                  1000,
                  demand.notch(),
                  false,
                  false,
                  6,
                  2.5,
                  braking,
                  .6,
                  .05);
          // Simulate a supported consist with proportional braking and normal traction.
          speed += force / 1000 * .05;
          room -= speed * .05;
          nearest = Math.min(nearest, room);
          if (Math.abs(speed) < .01 && room < .2) break;
        }
        check(
            nearest >= -.05,
            "signal braking must stop before the protected boundary at all tested speed/load ratios");
        check(
            Math.abs(speed) < .02, "a held red signal must settle instead of repeatedly creeping");
        check(
            AutomaticDriveModel.demand(200, 0, cap, braking).notch() > 0,
            "clearing a signal resumes traction without restarting the schedule");
      }
    System.out.println("SignalCheck: " + checks + " assertions passed");
  }
}
