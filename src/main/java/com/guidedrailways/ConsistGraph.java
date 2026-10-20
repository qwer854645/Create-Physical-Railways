package com.guidedrailways;

import java.util.*;

/** One deterministic partition of the current physical coupling graph. No game dependencies. */
public final class ConsistGraph {
  public record Link(UUID a, UUID b) {}

  private ConsistGraph() {}

  public static List<Set<UUID>> components(Collection<UUID> bodies, Collection<Link> links) {
    var adjacency = new TreeMap<UUID, Set<UUID>>();
    for (var body : bodies) adjacency.computeIfAbsent(body, k -> new TreeSet<>());
    for (var link : links) {
      adjacency.computeIfAbsent(link.a(), k -> new TreeSet<>()).add(link.b());
      adjacency.computeIfAbsent(link.b(), k -> new TreeSet<>()).add(link.a());
    }
    var result = new ArrayList<Set<UUID>>();
    var visited = new HashSet<UUID>();
    for (var root : adjacency.keySet()) {
      if (!visited.add(root)) continue;
      var members = new TreeSet<UUID>();
      var queue = new ArrayDeque<UUID>();
      members.add(root);
      queue.add(root);
      while (!queue.isEmpty()) {
        for (var next : adjacency.get(queue.remove())) {
          if (visited.add(next)) {
            members.add(next);
            queue.add(next);
          }
        }
      }
      result.add(Collections.unmodifiableSet(members));
    }
    return List.copyOf(result);
  }
}
