package com.guidedrailways;

import java.util.*;

/** Conservative sphere bounds in spatial cells, with deterministic original pair order. */
final class NearbyPairs {
  record Bounds(double x, double y, double z, double radius) {}

  record Pair(int a, int b) {}

  record Result(List<Pair> pairs, long distanceChecks) {}

  private record Cell(long x, long y, long z) {}

  private static long cell(double v) {
    return (long) Math.floor(v / 16);
  }

  static Result find(List<Bounds> bounds, double skin) {
    var cells = new HashMap<Cell, List<Integer>>();
    // A pair can recur in multiple cells, but only while inserting its later body.
    var seen = new int[bounds.size()];
    var pairs = new ArrayList<Pair>();
    long checks = 0;
    for (int i = 0; i < bounds.size(); i++) {
      var b = bounds.get(i);
      double r = b.radius() + skin / 2;
      if (!Double.isFinite(b.x())
          || !Double.isFinite(b.y())
          || !Double.isFinite(b.z())
          || !Double.isFinite(r)
          || r < 0) continue;
      for (long x = cell(b.x() - r); x <= cell(b.x() + r); x++)
        for (long y = cell(b.y() - r); y <= cell(b.y() + r); y++)
          for (long z = cell(b.z() - r); z <= cell(b.z() + r); z++) {
            var bucket = cells.computeIfAbsent(new Cell(x, y, z), key -> new ArrayList<>());
            for (int j : bucket) {
              if (seen[j] == i + 1) continue;
              seen[j] = i + 1;
              checks++;
              var a = bounds.get(j);
              double reach = a.radius() + b.radius() + skin,
                  dx = a.x() - b.x(),
                  dy = a.y() - b.y(),
                  dz = a.z() - b.z();
              if (dx * dx + dy * dy + dz * dz <= reach * reach) pairs.add(new Pair(j, i));
            }
            bucket.add(i);
          }
    }
    pairs.sort(Comparator.comparingInt(Pair::a).thenComparingInt(Pair::b));
    return new Result(List.copyOf(pairs), checks);
  }
}
