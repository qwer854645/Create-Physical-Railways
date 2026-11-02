package com.physicalrailways;

import java.util.*;
import org.joml.Vector3d;

public final class ManualRouteSelectionCheck {
  private static int checks;
  private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
  public static void main(String[] args) {
    var selection = new ManualRouteSelection();
    var head = new RouteMemory.Axle(UUID.randomUUID(), 0);
    var tail = new RouteMemory.Axle(UUID.randomUUID(), 1);
    var memory = new RouteMemory(Set.of(head, tail));
    var fork = new RouteMemory.Junction(UUID.randomUUID(), 10);
    int[] exits = {2, 3};
    check(selection.intent() == 0, "new controls start centered");
    selection.select(-1); var request = selection.snapshot();
    long before = memory.passageVersion();
    check(memory.preview(head, fork, 1, exits, 2, false, 1) == 2, "preview selects the requested exit");
    check(!selection.consume(request, memory.passageVersion() != before) && selection.intent() == -1,
        "lookahead and red-signal waiting cannot consume the choice");
    check(memory.commit(head, fork, 1, exits, 9, false, 1) == -1, "invalid exit refuses commit");
    check(!selection.consume(request, memory.passageVersion() != before), "failed commit retains choice");
    check(memory.commit(head, fork, 1, exits, 2, false, 2) == 2, "head locks its chosen exit");
    check(selection.consume(request, memory.passageVersion() != before) && selection.intent() == 0,
        "new passage centers the selector exactly once");
    selection.select(1); var next = selection.snapshot(); before = memory.passageVersion();
    check(memory.commit(tail, fork, 1, exits, 3, false, 3) == 2, "tail follows the locked left branch despite a new right request");
    check(!selection.consume(next, memory.passageVersion() != before) && selection.intent() == 1,
        "following axle cannot consume next-junction intent");
    var second = new RouteMemory.Junction(fork.graph(), 20); before = memory.passageVersion();
    check(memory.commit(head, second, 1, exits, 3, false, 4) == 3, "next junction can choose right independently");
    check(selection.consume(next, memory.passageVersion() != before), "next successful choice centers again");
    selection.select(-1); var stale = selection.snapshot();
    selection.select(0); selection.select(-1);
    check(!selection.consume(stale, true), "cancel and reselect cannot be consumed by an older prepared route");
    selection.select(0);
    check(!selection.consume(selection.snapshot(), true), "default routing is not a pending turn request");
    var up = new Vector3d(0, 1, 0);
    var outgoing = new Vector3d[] {new Vector3d(1, 0, 0), new Vector3d(-1, 0, 0)};
    check(BranchSelect.choose(new Vector3d(0, 0, 1), up, outgoing, BranchSelect.Steer.LEFT) == 0,
        "left follows forward travel, not console orientation");
    check(BranchSelect.choose(new Vector3d(0, 0, -1), up, outgoing, BranchSelect.Steer.LEFT) == 1,
        "reversing travel reverses world-space left");
    System.out.println("ManualRouteSelectionCheck: " + checks + " preview, commit, consist-following, revision and reverse checks passed");
  }
}
