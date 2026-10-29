package com.guidedrailways;

import java.util.*;
import org.joml.Vector3d;

public final class ConsistRouteCheck {
  static int checks;

  static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var a = new RouteMemory.Axle(UUID.randomUUID(), 0);
    var b = new RouteMemory.Axle(a.bogie(), 1);
    var c = new RouteMemory.Axle(UUID.randomUUID(), 0);
    var d = new RouteMemory.Axle(c.bogie(), 1);
    var carrier = UUID.randomUUID();
    var empty = new RouteMemory(Set.of());
    check(
        empty.reservationIdentity(carrier).equals(carrier),
        "fully detached consist has a stable carrier identity instead of throwing during physics tick");
    check(empty.active().isEmpty(), "detached consist holds no switch passages");
    var guided = new RouteMemory(Set.of(a, b, c, d));
    var expected = a.bogie().compareTo(c.bogie()) < 0 ? a.bogie() : c.bogie();
    check(
        guided.reservationIdentity(carrier).equals(expected),
        "guided identity remains the least bogie UUID");
    var detached = guided.copyFor(Set.of());
    check(
        detached.reservationIdentity(carrier).equals(carrier),
        "releasing all guides safely switches to carrier identity");
    check(
        detached.copyFor(Set.of(c, d)).reservationIdentity(carrier).equals(c.bogie()),
        "rerailing restores the actual guided identity");
    var ids = Set.of(a, b, c, d);
    var journal = new RouteMemory(ids);
    var node = new RouteMemory.Junction(UUID.randomUUID(), 100);
    check(
        journal.commit(a, node, 10, new int[] {20, 30}, 30, false) == 30,
        "physical leading axle chooses branch");
    check(
        journal.commit(a, node, 10, new int[] {20, 30}, 20, false) == 30,
        "same junction contact cannot record multiple passages or switch branch");
    for (var follower : List.of(b, c, d))
      check(
          journal.commit(follower, node, 10, new int[] {20, 30}, 20, false) == 30,
          "all axles of coupled carriers follow the same passage");
    for (var axle : ids) journal.clearRepeat(axle);
    for (var axle : List.of(d, c, b, a))
      check(
          journal.commit(axle, node, 30, new int[] {10, 40}, 40, false) == 10,
          "reverse motion retraces the actual entrance");
    for (var axle : ids) journal.clearRepeat(axle);
    check(
        journal.commit(a, node, 10, new int[] {20, 30}, 20, false) == 20,
        "after whole consist reverses clear, a fresh branch can be chosen");
    check(
        journal.commit(c, node, 10, new int[] {30}, 30, false) == -1,
        "missing recorded branch cannot redirect a follower to the wrong route");
    check(
        journal.commit(c, node, 10, new int[] {20, 30}, 30, false) == 20,
        "restoring branch retains the original choice");
    var loop = new RouteMemory(ids);
    for (int lap = 0; lap < 12; lap++) {
      int turn = lap % 2 == 0 ? 20 : 30;
      loop.clearRepeat(a);
      check(
          loop.commit(a, node, 10, new int[] {20, 30}, turn, false) == turn,
          "leader can revisit a junction before its tail");
    }
    for (var follower : List.of(b, c, d))
      for (int lap = 0; lap < 12; lap++) {
        loop.clearRepeat(follower);
        check(
            loop.commit(follower, node, 10, new int[] {20, 30}, 99, false)
                == (lap % 2 == 0 ? 20 : 30),
            "tail consumes repeated junction visits in order, without using newest visit early");
      }
    var split = loop.copyFor(Set.of(c, d));
    split.clearRepeat(c);
    check(
        split.commit(c, node, 10, new int[] {20, 30}, 30, false) == 30,
        "uncoupled component can create an independent next passage");
    loop.clearRepeat(a);
    check(
        loop.commit(a, node, 10, new int[] {20, 30}, 20, false) == 20,
        "other component retains independent history after split");
    var otherGraph = new RouteMemory.Junction(UUID.randomUUID(), 100);
    journal.clearRepeat(a);
    check(
        journal.commit(a, otherGraph, 10, new int[] {20, 30}, 30, false) == 30,
        "same numeric node in another graph is independent");
    var speculative = new RouteMemory(ids);
    check(
        speculative.preview(a, node, 10, new int[] {20, 30}, 20, true) == 20,
        "preview a candidate before checking its physical projection");
    check(
        speculative.preview(a, node, 10, new int[] {20, 30}, 30, true) == 30,
        "a rejected projection cannot lock the first speculative branch");
    check(
        speculative.commit(a, node, 10, new int[] {20, 30}, 30, true) == 30,
        "only an accepted crossing commits the chosen branch");
    for (var follower : List.of(b, c, d)) {
      check(
          speculative.preview(follower, node, 10, new int[] {20, 30}, 20, false) == 30,
          "followers preview the head's accepted passage");
      check(
          speculative.commit(follower, node, 10, new int[] {20, 30}, 30, false) == 30,
          "all coupled axles commit the same passage");
    }
    for (var axle : ids) speculative.clearRepeat(axle);
    check(
        speculative.preview(a, node, 30, new int[] {10, 40}, 40, true) == 40,
        "automatic trip may leave a fully cleared junction by a new scheduled exit");
    check(
        speculative.commit(a, node, 30, new int[] {10, 40}, 40, true) == 40,
        "the new trip is committed separately from historical reverse retracing");
    for (var follower : List.of(b, c, d))
      check(
          speculative.commit(follower, node, 30, new int[] {10, 40}, 10, false) == 40,
          "trailers follow the new trip rather than a completed older passage");
    var straddled = new RouteMemory(ids);
    straddled.commit(a, node, 10, new int[] {20, 30}, 30, false);
    straddled.clearRepeat(a);
    check(
        straddled.preview(a, node, 30, new int[] {10, 40}, 40, true) == 10,
        "a consist still spanning the junction must retrace its active passage");

    // Reported symptom: a scheduled train kept taking its previous branch at a junction and drove on
    // without ever reaching the destination on the other side. The repeat record outlived the
    // passage, and it outranked the planned exit.
    var trip = new RouteMemory(ids);
    check(
        trip.commit(a, node, 10, new int[] {20, 30}, 20, true) == 20,
        "the first lap takes the planned branch");
    for (var axle : List.of(b, c, d))
      trip.commit(axle, node, 10, new int[] {20, 30}, 20, false);
    check(
        trip.preview(a, node, 10, new int[] {20, 30}, 30, true) == 30,
        "a completed passage stops forcing its branch onto a new planned exit");
    check(
        trip.commit(a, node, 10, new int[] {20, 30}, 30, true) == 30,
        "the new branch can be committed on the next lap");
    // The safety property the record exists for still holds: while axles are crossing the junction,
    // the passage keeps the branch even when an automatic plan asks for the other one, so a consist
    // can never be split across the points.
    var spanning = new RouteMemory(ids);
    check(
        spanning.commit(a, node, 10, new int[] {20, 30}, 20, true) == 20,
        "the leading axle takes its branch");
    check(
        spanning.preview(a, node, 10, new int[] {20, 30}, 30, true) == 20,
        "a passage that is still being crossed outranks the plan");
    check(
        spanning.preview(a, node, 10, new int[] {20, 30}, 30, false) == 20,
        "a manual consist keeps retaking its active passage");
    var rejectedReverse = new RouteMemory(ids);
    for (var axle : ids) rejectedReverse.commit(axle, node, 10, new int[] {20, 30}, 30, false);
    for (var axle : ids) rejectedReverse.clearRepeat(axle);
    check(
        rejectedReverse.preview(d, node, 30, new int[] {10, 40}, 40, false) == 10,
        "manual reverse preview retains retracing behaviour");
    check(
        rejectedReverse.preview(d, node, 10, new int[] {20, 30}, 20, false) == 20,
        "rejecting a reverse projection must not mark that axle as having reversed");
    var forward = new Vector3d(0, 0, 1);
    var up = new Vector3d(0, 1, 0);
    for (double yaw : new double[] {0, .3, 1.4, 3.1})
      for (int sign : new int[] {-1, 1}) {
        var f = new Vector3d(forward).rotateY(yaw).mul(sign);
        var right = new Vector3d(f).cross(up);
        var entries =
            new org.joml.Vector3dc[] {
              new Vector3d(f).mul(.6).fma(.8, right),
              new Vector3d(f).mul(.98).fma(-.2, right),
              new Vector3d(f).mul(.8).fma(-.6, right)
            };
        check(
            BranchSelect.choose(f, up, entries, BranchSelect.Steer.NONE) == 1,
            "uncontrolled/no-target picks smallest angle even if no exact straight branch");
        check(
            BranchSelect.choose(f, up, entries, BranchSelect.Steer.RIGHT) == 0,
            "D selects rightmost relative to current travel direction");
        check(
            BranchSelect.choose(f, up, entries, BranchSelect.Steer.LEFT) == 2,
            "A selects leftmost relative to current travel direction");
      }
    // A passage whose axles will never all arrive used to outrank every later plan, so a train kept taking
    // the same branch while automatic navigation reported a mismatch each lap. It is now abandoned once it
    // has contradicted that many plans.
    var stuckAxle = new RouteMemory.Axle(UUID.randomUUID(), 0);
    var absent = new RouteMemory.Axle(stuckAxle.bogie(), 1);
    var stuck = new RouteMemory(Set.of(stuckAxle, absent));
    var stuckNode = new RouteMemory.Junction(UUID.randomUUID(), 22);
    check(stuck.commit(stuckAxle, stuckNode, 9, new int[] {14, 31}, 14, true) == 14, "first crossing takes the plan");
    // The first axle crossed 9 -> 14; the second axle never reaches the node, so the record stays open.
    int later = -1;
    for (int attempt = 0; attempt < 300 && later != 31; attempt++)
      later = stuck.preview(absent, stuckNode, 9, new int[] {14, 31}, 31, true);
    check(later == 31, "an unreachable passage eventually stops overriding the plan");

    System.out.println("ConsistRouteCheck: " + checks + " assertions passed");
  }
}
