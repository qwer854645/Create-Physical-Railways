package com.guidedrailways;

import java.util.*;

/**
 * One station per train and one train per station, with stable ownership and foreign reservations.
 */
public final class StationBindingSelection {
  public record Candidate(UUID train, UUID station, double distance) {}

  public static Map<UUID, UUID> choose(
      Collection<Candidate> candidates, Map<UUID, UUID> previous, Set<UUID> blocked) {
    var sorted =
        candidates.stream()
            .filter(c -> Double.isFinite(c.distance()) && !blocked.contains(c.station()))
            .sorted(
                Comparator.<Candidate>comparingInt(
                        c -> c.train().equals(previous.get(c.station())) ? 0 : 1)
                    .thenComparingDouble(c -> Math.abs(c.distance()))
                    .thenComparing(Candidate::station)
                    .thenComparing(Candidate::train))
            .toList();
    var selected = new HashMap<UUID, UUID>();
    var trains = new HashSet<UUID>();
    for (var c : sorted) {
      if (selected.containsKey(c.station()) || !trains.add(c.train())) continue;
      selected.put(c.station(), c.train());
    }
    return Map.copyOf(selected);
  }

  private StationBindingSelection() {}
}
