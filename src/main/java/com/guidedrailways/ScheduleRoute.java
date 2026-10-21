package com.guidedrailways;

import java.util.*;

/** Directed-edge Dijkstra: turns, approach direction and partial edge distances all matter. */
public final class ScheduleRoute {
  public record Edge(int from, int to) {}

  public interface Network {
    double length(Edge edge);

    List<Edge> exits(Edge edge);

    default boolean clear(Edge edge, double from, double to) {
      return true;
    }
  }

  public record Goal(UUID id, String name, Edge edge, double offset) {}

  public record Route(List<Edge> edges, Goal goal, double distance) {
    /** Physics may cross several short edges between two server ticks. */
    public int progress(Edge observed, int current) {
      if (edges.isEmpty()) return -1;
      int at = Math.clamp(current, 0, edges.size() - 1);
      if (edges.get(at).equals(observed)) return at;
      if (at > 0 && edges.get(at - 1).equals(observed)) return at - 1;
      for (int i = at + 1; i < edges.size(); i++) if (edges.get(i).equals(observed)) return i;
      return -1;
    }

    public int next(int from, int via, int start) {
      for (int i = Math.max(0, start - 1); i + 1 < edges.size(); i++)
        if (edges.get(i).equals(new Edge(from, via))) return edges.get(i + 1).to;
      return -1;
    }

    public double remaining(Network network, int leg, double offset) {
      double distance = -offset;
      for (int i = leg; i < edges.size(); i++)
        distance += i == edges.size() - 1 ? goal.offset : network.length(edges.get(i));
      return distance;
    }
  }

  private record Key(Edge edge, boolean initial) {}

  private record Visit(Key key, double cost, Visit previous) {}

  public static Route find(
      Network network, Edge start, double offset, List<Goal> goals, double arrivalTolerance) {
    return find(network, start, offset, goals, arrivalTolerance, Double.POSITIVE_INFINITY);
  }

  public static Route find(
      Network network,
      Edge start,
      double offset,
      List<Goal> goals,
      double arrivalTolerance,
      double maximumDistance) {
    if (!Double.isFinite(network.length(start))
        || !Double.isFinite(offset)
        || offset < 0
        || offset > network.length(start) + 1e-6) return null;
    var pending =
        new PriorityQueue<Visit>(
            Comparator.comparingDouble(Visit::cost)
                .thenComparingInt(v -> v.key.edge.from)
                .thenComparingInt(v -> v.key.edge.to));
    var best = new HashMap<Key, Double>();
    var first = new Visit(new Key(start, true), -offset, null);
    pending.add(first);
    best.put(first.key, first.cost);
    double answerCost = Double.POSITIVE_INFINITY;
    Visit answer = null;
    Goal target = null;
    int visited = 0;
    while (!pending.isEmpty() && visited++ < 32768) {
      var v = pending.remove();
      if (v.cost > best.getOrDefault(v.key, Double.POSITIVE_INFINITY) + 1e-9) continue;
      if (v.cost > answerCost || v.cost > maximumDistance) continue;
      for (var goal : goals)
        if (goal.edge.equals(v.key.edge)
            && goal.offset >= 0
            && goal.offset <= network.length(goal.edge) + 1e-6) {
          double cost = v.cost + goal.offset;
          if (cost >= -Math.max(0, arrivalTolerance) - 1e-6
              && cost < answerCost
              && cost <= maximumDistance
              && network.clear(v.key.edge, v.key.initial ? offset : 0, goal.offset)) {
            answer = v;
            target = goal;
            answerCost = Math.max(0, cost);
          }
        }
      if (!network.clear(v.key.edge, v.key.initial ? offset : 0, network.length(v.key.edge)))
        continue;
      var exits = new ArrayList<>(network.exits(v.key.edge));
      exits.sort(Comparator.comparingInt(Edge::to));
      for (var edge : exits) {
        double length = network.length(edge);
        if (!Double.isFinite(length) || length <= 0) continue;
        double cost = v.cost + network.length(v.key.edge);
        var key = new Key(edge, false);
        if (cost + 1e-9 < best.getOrDefault(key, Double.POSITIVE_INFINITY)) {
          best.put(key, cost);
          pending.add(new Visit(key, cost, v));
        }
      }
    }
    if (answer == null) return null;
    var edges = new ArrayList<Edge>();
    for (var v = answer; v != null; v = v.previous) edges.add(v.key.edge);
    Collections.reverse(edges);
    return new Route(List.copyOf(edges), target, answerCost);
  }
}
