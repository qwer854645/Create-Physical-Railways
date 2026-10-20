package com.guidedrailways;

/** Cheap change stamp; graph instances/membership and periodic audits cover external mutations. */
public final class TrackTopology {
  private static final java.util.concurrent.atomic.AtomicLong REVISION =
      new java.util.concurrent.atomic.AtomicLong();

  public static void changed() {
    REVISION.incrementAndGet();
  }

  static long revision() {
    return REVISION.get();
  }
}
