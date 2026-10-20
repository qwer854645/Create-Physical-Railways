package com.guidedrailways;

import java.util.*;

public final class NearbyPairsCheck {
  public static void main(String[] args) {
    var random = new Random(674320L);
    int count = 0;
    for (int scene = 0; scene < 200; scene++) {
      var bounds = new ArrayList<NearbyPairs.Bounds>();
      for (int i = 0; i < 100; i++)
        bounds.add(
            new NearbyPairs.Bounds(
                random.nextDouble() * 160 - 80,
                random.nextDouble() * 40 - 20,
                random.nextDouble() * 160 - 80,
                .1 + random.nextDouble() * 12));
      var expected = new ArrayList<NearbyPairs.Pair>();
      for (int i = 0; i < bounds.size(); i++)
        for (int j = i + 1; j < bounds.size(); j++) {
          var a = bounds.get(i);
          var b = bounds.get(j);
          double dx = a.x() - b.x(),
              dy = a.y() - b.y(),
              dz = a.z() - b.z(),
              r = a.radius() + b.radius() + BogiePairContact.SKIN;
          if (dx * dx + dy * dy + dz * dz <= r * r) expected.add(new NearbyPairs.Pair(i, j));
        }
      var actual = NearbyPairs.find(bounds, BogiePairContact.SKIN);
      if (!actual.pairs().equals(expected))
        throw new AssertionError("broadphase missed or reordered a candidate");
      count++;
    }
    // Every body overlaps eight cells: deduplication must reset for each later body.
    var dense = new ArrayList<NearbyPairs.Bounds>();
    for (int i = 0; i < 500; i++) dense.add(new NearbyPairs.Bounds(0, 0, 0, 2));
    var packed = NearbyPairs.find(dense, BogiePairContact.SKIN);
    if (packed.distanceChecks() != 124750 || packed.pairs().size() != 124750)
      throw new AssertionError("dense multi-cell pairs lost or tested repeatedly");
    for (int i = 1; i < packed.pairs().size(); i++) {
      var a = packed.pairs().get(i - 1);
      var b = packed.pairs().get(i);
      if (a.a() > b.a() || a.a() == b.a() && a.b() >= b.b())
        throw new AssertionError("dense contact order changed");
    }
    var dispersed = new ArrayList<NearbyPairs.Bounds>();
    for (int i = 0; i < 1000; i++) dispersed.add(new NearbyPairs.Bounds(i * 100, 0, 0, 2));
    var result = NearbyPairs.find(dispersed, BogiePairContact.SKIN);
    if (!result.pairs().isEmpty() || result.distanceChecks() != 0)
      throw new AssertionError("far bodies still compared");
    var boundary =
        List.of(new NearbyPairs.Bounds(-.06, 0, 0, .01), new NearbyPairs.Bounds(.06, 0, 0, .01));
    if (NearbyPairs.find(boundary, .12).pairs().size() != 1)
      throw new AssertionError("skin across a negative cell boundary lost");
    System.out.println(
        "NearbyPairsCheck: "
            + count
            + " random scenes match all-pairs reference; 1000 dispersed bodies require 0 distance checks instead of 499500");
  }
}
