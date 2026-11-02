package com.physicalrailways;

/** Cheap change stamp; graph instances/membership and periodic audits cover external mutations. */
public final class TrackTopology {
  private static final java.util.concurrent.atomic.AtomicLong REVISION =
      new java.util.concurrent.atomic.AtomicLong();
  private static final java.util.Map<com.simibubi.create.content.trains.graph.TrackGraph, Long> GRAPHS =
      new java.util.WeakHashMap<>();

  public static synchronized void changed(com.simibubi.create.content.trains.graph.TrackGraph graph) {
    GRAPHS.put(graph, GRAPHS.getOrDefault(graph, 0L) + 1);
  }

  static synchronized long revision(com.simibubi.create.content.trains.graph.TrackGraph graph) {
    return REVISION.get() + GRAPHS.getOrDefault(graph, 0L);
  }

  public static void changed() {
    REVISION.incrementAndGet();
  }

  static long revision() {
    return REVISION.get();
  }
}
