package com.guidedrailways;

import java.util.UUID;

/** How the switch queue orders waiters: priority first, then arrival, with starvation protection. */
public final class SwitchPriorityCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected switch priority: " + what);
  }

  public static void main(String[] args) {
    var key = new SwitchReservations.Key(UUID.randomUUID(), 22);
    var low = UUID.randomUUID();
    var high = UUID.randomUUID();
    var old = UUID.randomUUID();

    var queue = new SwitchQueue();
    queue.wait(key, low, 1, 2, 100, 1);
    queue.wait(key, high, 1, 2, 100, 5);
    check(
        queue.head(key, 105).train().equals(high),
        "a higher priority waiter goes first even when it arrived at the same tick");

    // Same priority keeps arrival order.
    var second = new SwitchQueue();
    var firstTrain = UUID.randomUUID();
    var laterTrain = UUID.randomUUID();
    second.wait(key, firstTrain, 1, 2, 100, 3);
    second.wait(key, laterTrain, 1, 2, 104, 3);
    check(
        second.head(key, 105).train().equals(firstTrain),
        "equal priority keeps the earliest waiter first");

    // A patient waiter is not starved: past the starvation window it outranks fresh high priorities.
    var aging = new SwitchQueue();
    var patient = UUID.randomUUID();
    // A consist that keeps asking refreshes its intent; only its arrival tick stays old. Without these
    // refreshes the intent would lapse for lack of interest, which is a different rule.
    for (long tick = 100; tick <= 100 + SwitchQueue.STARVE_TICKS; tick += 10)
      aging.wait(key, patient, 1, 2, tick, 0);
    long now = 100 + SwitchQueue.STARVE_TICKS + 1;
    aging.wait(key, high, 1, 2, now, 5);
    check(
        aging.head(key, now).train().equals(patient),
        "a waiter past the starvation window outranks a fresh maximum priority");

    // Before that window the priority still decides.
    var fresh = new SwitchQueue();
    var patient2 = UUID.randomUUID();
    fresh.wait(key, patient2, 1, 2, 100, 0);
    fresh.wait(key, high, 1, 2, 105, 5);
    check(
        fresh.head(key, 106).train().equals(high),
        "before the window a high priority still wins");

    // A lapsed intent is not eligible.
    var lapsed = new SwitchQueue();
    lapsed.wait(key, old, 1, 2, 100, 5);
    check(
        lapsed.head(key, 100 + SwitchQueue.INTENT_TICKS + 1) == null,
        "an unrefreshed intent lapses out of the queue");

    // Clamping: out-of-range priorities are bounded rather than reordering unexpectedly.
    var clamped = new SwitchQueue();
    var extreme = UUID.randomUUID();
    var normal = UUID.randomUUID();
    clamped.wait(key, normal, 1, 2, 100, 99);
    clamped.wait(key, extreme, 1, 2, 101, -4);
    check(
        clamped.head(key, 102).train().equals(normal),
        "an over-large priority clamps to the maximum and still wins");

    System.out.println("SwitchPriorityCheck: " + checks + " switch priority decisions passed");
  }
}
