package com.guidedrailways;

import java.util.*;

public final class StationBindingCheck {
  private static int checks;

  private static void check(boolean ok, String label) {
    checks++;
    if (!ok) throw new AssertionError(label);
  }

  private static UUID id(long value) {
    return new UUID(0, value);
  }

  public static void main(String[] args) {
    var a = id(1);
    var b = id(2);
    var x = id(10);
    var y = id(11);
    var ax = new StationBindingSelection.Candidate(a, x, .15);
    var bx = new StationBindingSelection.Candidate(b, x, .01);
    var ay = new StationBindingSelection.Candidate(a, y, .1);
    check(
        StationBindingSelection.choose(List.of(ax), Map.of(), Set.of()).equals(Map.of(x, a)),
        "arrived train is assigned");
    check(
        StationBindingSelection.choose(List.of(ax), Map.of(), Set.of(x)).isEmpty(),
        "native reservation cannot be stolen");
    check(
        StationBindingSelection.choose(List.of(ax, bx), Map.of(x, a), Set.of())
            .equals(Map.of(x, a)),
        "stable owner survives jitter");
    check(
        StationBindingSelection.choose(List.of(bx), Map.of(x, a), Set.of()).equals(Map.of(x, b)),
        "departure permits a new owner");
    check(
        StationBindingSelection.choose(List.of(), Map.of(x, a), Set.of()).isEmpty(),
        "movement/removal/unload releases station");
    var random = new Random(4731);
    var candidates = new ArrayList<>(List.of(ax, bx, ay));
    var expected = StationBindingSelection.choose(candidates, Map.of(), Set.of());
    for (int i = 0; i < 1000; i++) {
      Collections.shuffle(candidates, random);
      var chosen = StationBindingSelection.choose(candidates, Map.of(), Set.of());
      check(chosen.equals(expected), "selection independent of iteration order");
      check(
          new HashSet<>(chosen.values()).size() == chosen.size(), "at most one station per train");
      check(chosen.size() == 2, "at most one train per station, alternate station remains usable");
    }
    check(
        StationBindingSelection.choose(List.of(ax, ay), Map.of(), Set.of()).size() == 1,
        "overlapping stations cannot claim the same train");
    check(
        StationBindingSelection.choose(
                List.of(new StationBindingSelection.Candidate(a, x, Double.NaN)),
                Map.of(),
                Set.of())
            .isEmpty(),
        "invalid distances never claim a station");
    var stop = new StationStopState();
    for (int i = 0; i < 5; i++)
      check(!stop.tick(.15, .05, true), "passing has no premature arrival");
    check(stop.tick(.15, .05, true), "actual stop debounces for six ticks");
    check(!stop.tick(.15, .25, true), "departure clears stop");
    for (int i = 0; i < 20; i++) check(!stop.tick(0, 10, true), "high speed pass never binds");
    var restored = new StationStopState();
    for (int i = 0; i < 5; i++)
      check(
          !restored.tick(.1, 0, true),
          "reload revalidates the real stop instead of trusting a saved flag");
    check(restored.tick(.1, 0, true), "station presence can be reconstructed after reload");
    check(!restored.tick(.1, 0, false), "missing station/track invalidates the reconstructed stop");
    check(
        StationBindingSelection.choose(
                List.of(
                    new StationBindingSelection.Candidate(a, x, .8),
                    new StationBindingSelection.Candidate(a, x, .1)),
                Map.of(),
                Set.of())
            .equals(Map.of(x, a)),
        "two endpoint candidates still publish one train/station association");
    System.out.println(
        "StationBindingCheck: " + checks + " station ownership and stop checks passed");
  }
}
