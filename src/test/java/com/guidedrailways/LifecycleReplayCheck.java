package com.guidedrailways;

import java.util.*;

/** Stateful replays through the real recovery, identity, route and station models. */
public final class LifecycleReplayCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static void recovery() {
    var queue = new RecoveryQueue<Integer>();
    var remaining = new HashMap<Integer, Integer>();
    for (int i = 0; i < 1000; i++) {
      remaining.put(i, 3);
      queue.add(i);
      queue.add(i);
    }
    int total = 0;
    while (!remaining.isEmpty()) {
      var visited = new HashSet<Integer>();
      int work =
          queue.drain(
              128,
              id -> {
                check(visited.add(id), "other bodies receive work before a body retries");
                int next = remaining.compute(id, (key, old) -> old - 1);
                if (next == 0) remaining.remove(id);
                return next == 0;
              });
      check(work <= 128 && work > 0, "discovery work has a hard per-tick budget");
      total += work;
    }
    check(total == 3000, "duplicate load events do not duplicate scans");
    check(
        queue.drain(
                128,
                id -> {
                  throw new AssertionError("finished work retained");
                })
            == 0,
        "completed work releases queue entries");
    queue.add(1);
    queue.remove(1);
    check(queue.drain(128, id -> false) == 0, "unloaded body is not retried");
    queue.add(2);
    queue.clear();
    check(queue.drain(128, id -> false) == 0, "world unload clears pending work");
    queue.add(3);
    try {
      queue.drain(
          1,
          id -> {
            throw new IllegalStateException("transient recovery failure");
          });
      throw new AssertionError("failure swallowed");
    } catch (IllegalStateException expected) {
      check(queue.drain(1, id -> id == 3) == 1, "failed work remains recoverable");
    }
  }

  private static void journeys() {
    for (int cycle = 0; cycle < 100; cycle++) {
      var a = new UUID(0, cycle * 10L + 1);
      var b = new UUID(0, cycle * 10L + 2);
      var other = new UUID(0, cycle * 10L + 3);
      var members = Set.of(a, b);
      var ledger = new TrainIdentityLedger();
      var train = ledger.reconcile(List.of(members), Set.of(members)).get(members);
      var links = new CouplingLinks();
      var pair = new CouplingLinks.Link(UUID.randomUUID(), a, b);
      links.add(pair);
      var head = new RouteMemory.Axle(a, 0);
      var tail = new RouteMemory.Axle(b, 0);
      var route = new RouteMemory(Set.of(head, tail));
      var junction = new RouteMemory.Junction(UUID.randomUUID(), 9);
      check(
          route.commit(head, junction, 1, new int[] {2, 3}, 3, true) == 3,
          "planned branch recorded");
      // An unrelated placement must not replace an existing train or its route memory.
      ledger.reconcile(List.of(members, Set.of(other)), Set.of(members));
      check(
          route.preview(tail, junction, 1, new int[] {2, 3}, 2, false) == 3,
          "new unrelated body cannot redirect a trailer");
      check(
          ledger.reconcile(List.of(Set.of(a)), Set.of(Set.of(a))).isEmpty(),
          "partial load cannot publish a split identity");
      check(links.forEndpoint(a).equals(pair), "unloading preserves saved coupling");
      var reload = new ScheduleReloadGate(members);
      for (int tick = 0; tick < 50; tick++)
        check(
            reload.tick(Set.of(a), tick, true) == ScheduleReloadGate.Result.WAIT,
            "schedule waits for missing trailer");
      for (int tick = 50; tick < 59; tick++)
        check(
            reload.tick(members, tick, true) == ScheduleReloadGate.Result.WAIT,
            "restored train settles before resuming");
      check(
          reload.tick(members, 59, true) == ScheduleReloadGate.Result.READY,
          "complete train resumes");
      check(
          train.equals(ledger.reconcile(List.of(members), Set.of(members)).get(members)),
          "identity survives unload/reload");
      var restoredRoute = route.copyFor(Set.of(head, tail));
      check(
          restoredRoute.commit(tail, junction, 1, new int[] {2, 3}, 2, false) == 3,
          "trailer follows established passage after reconstruction");
      links.remove(pair.id());
      check(
          links.forEndpoint(a) == null && links.forEndpoint(b) == null,
          "unlink clears both endpoints");
      var split = ledger.reconcile(List.of(Set.of(a), Set.of(b)), Set.of(Set.of(a)));
      check(
          train.equals(split.get(Set.of(a))) && !split.containsKey(Set.of(b)),
          "controlled split retains identity; unpowered trailer is not a train");
      links.add(pair);
      check(
          train.equals(ledger.reconcile(List.of(members), Set.of(members)).get(members)),
          "recoupling restores one identity");
      var station = new StationStopState();
      for (int i = 0; i < 5; i++) check(!station.tick(.1, 0, true), "arrival debounces");
      check(station.tick(.1, 0, true), "stable train is present");
      check(!station.tick(.1, 1, true), "departure releases presence");
      check(!station.tick(.1, 0, false), "unload cannot retain station presence");
    }
  }

  public static void main(String[] args) {
    recovery();
    journeys();
    System.out.println(
        "LifecycleReplayCheck: "
            + checks
            + " budget and multi-stage load, route, split, recouple and station checks passed");
  }
}
