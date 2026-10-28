package com.guidedrailways;

import java.util.UUID;

/**
 * Switch access: a holder keeps its passage, a freed switch goes to the oldest waiter, and a
 * long standstill is reported instead of silently stopping both consists.
 */
public final class SwitchReservationsCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var graph = UUID.randomUUID();
    var key = new SwitchReservations.Key(graph, 7);
    var east = UUID.randomUUID();
    var west = UUID.randomUUID();
    var third = UUID.randomUUID();

    // A holder keeps the switch while it is still passing, which is what reverse traversal needs.
    var reservations = new SwitchReservations();
    check(reservations.claim(key, east, 1, 2, 100), "the first consist takes a free switch");
    check(reservations.claim(key, east, 1, 2, 110), "the holder keeps it on refresh");
    check(reservations.claim(key, east, 2, 1, 120), "the holder keeps it when traversing back");
    check(!reservations.claim(key, west, 1, 2, 121), "another consist cannot take a held switch");
    check(
        reservations.owns(key, east, 2, 1, 121),
        "reverse ownership is preserved for the holder");

    // When the claim lapses the switch is free again; the consist that asked first gets it, even if
    // a different consist happens to ask in the very same tick.
    long afterExpiry = 120 + SwitchReservations.HOLD_TICKS + 1;
    check(!reservations.owns(key, east, 2, 1, afterExpiry), "a claim lapses when not refreshed");
    check(
        reservations.claim(key, west, 1, 2, afterExpiry),
        "the switch is free once the claim lapsed");
    check(reservations.owns(key, west, 1, 2, afterExpiry), "the new holder owns it");

    // Queue order: the oldest waiter is served first once the switch is free, and an intent that is
    // never refreshed lapses so an abandoned approach cannot block a junction forever.
    var queue = new SwitchQueue();
    queue.wait(key, west, 1, 2, 200);
    queue.wait(key, third, 1, 2, 210);
    check(queue.size(key, 210) == 2, "both intents are queued");
    check(queue.head(key, 210).train().equals(west), "the older intent is first");
    queue.wait(key, west, 1, 2, 250);
    check(queue.oldestWait(key, 260) == 60, "a refreshed intent keeps its original start tick");
    check(queue.size(key, 260) == 1, "the intent that was not refreshed lapsed");
    check(queue.head(key, 260).train().equals(west), "the refreshed intent is still first");
    queue.clear(key, west);
    check(queue.head(key, 300) == null, "clearing the last intent empties the queue");

    // FIFO is enforced on a freed switch: the late asker cannot jump ahead of the older waiter.
    var fairness = new SwitchReservations();
    check(fairness.claim(key, east, 1, 2, 400), "east takes the switch");
    check(!fairness.claim(key, west, 1, 2, 401), "west is refused and queued");
    check(!fairness.claim(key, third, 1, 2, 402), "third is queued behind west");
    long freed = 400 + SwitchReservations.HOLD_TICKS + 1;
    check(!fairness.claim(key, third, 1, 2, freed), "the younger waiter cannot jump the queue");
    check(fairness.claim(key, west, 1, 2, freed), "the oldest waiter is served when it is free");
    check(fairness.owns(key, west, 1, 2, freed), "the queued consist now owns the switch");

    // A consist must not take a second branch while its axles are still crossing the first, but it must
    // not hold a finished crossing against itself either: the recorded stall waited 264 ticks at one
    // node because the claim it owned could not be used for the passage it needed.
    var self = new SwitchReservations();
    check(self.claim(key, east, 1, 2, 1000), "a consist takes its first passage");
    check(!self.claim(key, east, 3, 4, 1001), "a fresh claim blocks a second branch");
    check(self.claim(key, east, 1, 2, 1002), "the holder keeps refreshing its passage");
    long stale = 1002 + SwitchReservations.HOLD_TICKS;
    check(
        self.claim(key, east, 3, 4, stale),
        "a claim nobody refreshed for half its life yields to the consist's newer passage");
    check(self.owns(key, east, 3, 4, stale), "the newer passage is the owned one");

    System.out.println(
        "SwitchReservationsCheck: " + checks + " switch passage and fairness checks passed");
  }
}
