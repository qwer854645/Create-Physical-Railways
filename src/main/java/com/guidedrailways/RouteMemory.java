package com.guidedrailways;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Which branch a consist takes at a junction, in the order the consist actually meets it.
 *
 * <p>A junction keeps a queue of passages, oldest first, and every axle consumes them in that order. That ordering is
 * why this class exists: a long consist meets the same junction several times, and the tail must take the branch the
 * head committed to, in the order the head committed to it, never the newest one early.
 *
 * <p>Decisions, in order:
 *
 * <ol>
 *   <li><b>An unconsumed passage wins.</b> The axle takes the branch of the oldest passage it has not consumed, in
 *       whichever direction it crosses: entering from that passage's exit means retracing it, because a carriage
 *       cannot uncross itself.
 *   <li><b>A reverse still in progress wins.</b> After the whole consist has crossed, the queue is quiet and every
 *       passage is consumed; a train that reverses back through the junction must still leave by the branch it
 *       entered by, and stays bound to that until every axle has come back through. That is what separates
 *       "reversing away from the junction" from "a fresh crossing from this side", which may follow the plan again.
 *   <li><b>The plan wins over a fresh passage.</b> With nothing to consume and no reverse in progress, a new passage
 *       opens using the planned exit when the route has one. The memory records the route; it does not overrule it.
 *   <li><b>The last branch wins next</b>, so a manual train repeats a junction the way it left it and a second touch
 *       of the same junction cannot silently switch branch.
 *   <li><b>Otherwise the proposed branch is taken</b>, which is what the geometry suggests.
 * </ol>
 *
 * <p>Passages expire. The previous design asked whether a passage was "complete", meaning every axle had passed, and
 * any consist with an axle that never reaches the node left an entry that never completed and outranked every later
 * plan for ever. A passage untouched for {@link #PASSAGE_TICKS} is dropped, so a stale entry costs one lap at most.
 */
public final class RouteMemory {
  public record Axle(UUID bogie, int end) {}

  public record Junction(UUID graph, int node) {}

  public record Active(Junction node, int from, int to) {}

  /** Passages untouched for this long are dropped, so a stale entry cannot outlive its lap. */
  static final long PASSAGE_TICKS = 200;

  /** Passages remembered per junction. */
  static final int MAX_PASSAGES = 64;

  private static final class Passage {
    final Junction node;
    final int from, to;
    long touched;
    final Set<Axle> used = new HashSet<>();
    /** Axles that have come back through this passage from its exit side. */
    final Set<Axle> back = new HashSet<>();

    Passage(Junction node, int from, int to, long now) {
      this.node = node;
      this.from = from;
      this.to = to;
      this.touched = now;
    }

    boolean expired(long now) {
      return now >= 0 && touched >= 0 && now - touched >= PASSAGE_TICKS;
    }
  }

  private final Set<Axle> members;
  private final Map<Junction, Deque<Passage>> passages = new HashMap<>();
  private final Map<Junction, Integer> dropped = new HashMap<>();
  private final Map<Axle, Map<Junction, Integer>> consumed = new HashMap<>();
  private final Map<Axle, Map<Junction, Integer>> repeat = new HashMap<>();

  public RouteMemory(Set<Axle> members) {
    this.members = Set.copyOf(members);
  }

  public Set<Axle> members() {
    return members;
  }

  /** Detached consists have no guided axles; retain a real carrier identity until rerailing. */
  public UUID reservationIdentity(UUID carrier) {
    return members.stream()
        .map(Axle::bogie)
        .min(UUID::compareTo)
        .orElse(Objects.requireNonNull(carrier));
  }

  /** True when this consist already recorded that passage, in either direction. */
  public boolean recorded(Junction node, int from, int to) {
    var queue = passages.get(node);
    if (queue != null)
      for (var passage : queue)
        if (passage.from == from && passage.to == to || passage.from == to && passage.to == from) return true;
    for (var perNode : repeat.values()) {
      var last = perNode.get(node);
      if (last != null && (last == to || last == from)) return true;
    }
    return false;
  }

  /** The passages still open at each junction. */
  public List<Active> active() {
    var out = new ArrayList<Active>();
    for (var queue : passages.values())
      for (var passage : queue) out.add(new Active(passage.node, passage.from, passage.to));
    return List.copyOf(out);
  }

  public RouteMemory copyFor(Set<Axle> next) {
    var copy = new RouteMemory(next);
    for (var entry : passages.entrySet()) copy.passages.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
    copy.dropped.putAll(dropped);
    for (var axle : next) {
      var counters = consumed.get(axle);
      if (counters != null) copy.consumed.put(axle, new HashMap<>(counters));
      var perNode = repeat.get(axle);
      if (perNode != null) copy.repeat.put(axle, new HashMap<>(perNode));
    }
    return copy;
  }

  public void clearRepeat(Axle axle) {
    repeat.remove(axle);
  }

  public int preview(Axle axle, Junction node, int from, int[] exits, int proposed, boolean planned) {
    return select(axle, node, from, exits, proposed, false, planned, -1);
  }

  public int commit(Axle axle, Junction node, int from, int[] exits, int proposed, boolean planned) {
    return select(axle, node, from, exits, proposed, true, planned, -1);
  }

  /** Clocked variants: the clock is what lets a passage expire while an axle never arrives. */
  public int preview(
      Axle axle, Junction node, int from, int[] exits, int proposed, boolean planned, long now) {
    return select(axle, node, from, exits, proposed, false, planned, now);
  }

  public int commit(
      Axle axle, Junction node, int from, int[] exits, int proposed, boolean planned, long now) {
    return select(axle, node, from, exits, proposed, true, planned, now);
  }

  private int select(
      Axle axle,
      Junction node,
      int from,
      int[] exits,
      int proposed,
      boolean commit,
      boolean planned,
      long now) {
    if (!members.contains(axle)) throw new IllegalArgumentException("unknown axle");

    var queue = passages.computeIfAbsent(node, key -> new ArrayDeque<>());
    expire(node, queue, now);

    int index = indexOf(axle, node);
    while (index < queue.size()) {
      var passage = passageAt(queue, index);
      boolean forward = passage.from == from;
      boolean backward = passage.to == from;
      if (!forward && !backward) {
        // Not this axle's passage: it met the junction from another direction, so it is skipped for good. Advancing
        // the pointer keeps the ordering of the passages that do concern this axle.
        if (commit) consume(axle, node, index + 1);
        index++;
        continue;
      }
      if (commit) {
        passage.touched = now;
        if (forward) passage.used.add(axle);
        else passage.back.add(axle);
        consume(axle, node, index + 1);
      }
      int branch = forward ? passage.to : passage.from;
      return contains(exits, branch) ? branch : -1;
    }

    var newest = queue.peekLast();
    if (newest != null && newest.to == from && newest.back.size() < members.size()) {
      // Reversing back through a junction this consist just crossed: it leaves by the branch it entered by, and stays
      // bound to that until every axle has come back through.
      if (commit) {
        newest.touched = now;
        newest.back.add(axle);
        remember(axle, node, newest.from);
      }
      return contains(exits, newest.from) ? newest.from : -1;
    }

    int chosen;
    if (planned && proposed >= 0) chosen = proposed;
    else {
      var last = repeat.getOrDefault(axle, Map.of()).get(node);
      chosen = last != null ? last : proposed;
    }
    if (!contains(exits, chosen)) return -1;
    if (!commit) return chosen;

    var created = new Passage(node, from, chosen, now);
    created.used.add(axle);
    queue.addLast(created);
    while (queue.size() > MAX_PASSAGES) {
      queue.removeFirst();
      dropped.merge(node, 1, Integer::sum);
    }
    consume(axle, node, queue.size());
    remember(axle, node, chosen);
    return chosen;
  }

  private void expire(Junction node, Deque<Passage> queue, long now) {
    while (!queue.isEmpty() && queue.peekFirst().expired(now)) {
      queue.removeFirst();
      dropped.merge(node, 1, Integer::sum);
    }
  }

  private int indexOf(Axle axle, Junction node) {
    int absolute = consumed.getOrDefault(axle, Map.of()).getOrDefault(node, 0);
    return Math.max(0, absolute - dropped.getOrDefault(node, 0));
  }

  private void consume(Axle axle, Junction node, int index) {
    consumed
        .computeIfAbsent(axle, key -> new HashMap<>())
        .put(node, index + dropped.getOrDefault(node, 0));
  }

  private void remember(Axle axle, Junction node, int to) {
    repeat.computeIfAbsent(axle, key -> new HashMap<>()).put(node, to);
  }

  private static Passage passageAt(Deque<Passage> queue, int index) {
    int i = 0;
    for (var passage : queue) if (i++ == index) return passage;
    throw new IllegalArgumentException("passage index out of range");
  }

  private static boolean contains(int[] exits, int exit) {
    for (int candidate : exits) if (candidate == exit) return true;
    return false;
  }
}
