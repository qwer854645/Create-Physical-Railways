package com.guidedrailways;

import java.util.Objects;

/**
 * Durable binding of one lower body to one plate location plus a monotonically increasing move
 * revision. Free of Minecraft types so the rule can be checked offline.
 *
 * <p>A location is reusable: a plate can be removed and another one placed on the same block, and
 * the lower body keeps recording the same coordinates. Only the revision tells the current claim
 * apart from an earlier one, so a handoff composed at revision N can no longer rewrite a body that
 * has since moved on to another revision.
 */
record MountOwnership(Location location, long revision) {
  /** A handoff composed while the lower body could not be observed; the source is unverifiable. */
  static final long UNKNOWN_REVISION = -1;

  record Location(String dimension, long position) {
    Location {
      Objects.requireNonNull(dimension, "dimension");
    }
  }

  enum Result {
    /** The claim moved; the returned ownership carries the new revision. */
    APPLIED,
    /** The claim already records this destination, so a repeated callback changes nothing. */
    REPLAYED,
    /** The recorded location is not the authorized source of this move. */
    REJECTED_SOURCE,
    /** The recorded revision is not the one the caller observed when it authorized the move. */
    REJECTED_STALE,
    /** The revision cannot advance any further, so no write happens. */
    REJECTED_OVERFLOW
  }

  record Outcome(Result result, MountOwnership ownership) {
    boolean changed() {
      return result == Result.APPLIED;
    }
  }

  MountOwnership {
    Objects.requireNonNull(location, "location");
    if (revision < 0) throw new IllegalArgumentException("Negative ownership revision");
  }

  /** First claim of a location that no lower body recorded yet. */
  static MountOwnership firstClaim(Location location) {
    return new MountOwnership(location, 0);
  }

  boolean matches(Location other) {
    return location.equals(other);
  }

  /**
   * Moves the claim to {@code destination} only while it is still at {@code source} and, when the
   * caller could observe it, still at {@code expectedRevision}. Replaying the exact move is
   * idempotent; every rejection leaves this ownership untouched.
   */
  Outcome moveTo(Location source, Location destination, long expectedRevision) {
    if (matches(destination)) return new Outcome(Result.REPLAYED, this);
    if (!matches(source)) return new Outcome(Result.REJECTED_SOURCE, this);
    if (expectedRevision != UNKNOWN_REVISION && expectedRevision != revision)
      return new Outcome(Result.REJECTED_STALE, this);
    if (revision == Long.MAX_VALUE) return new Outcome(Result.REJECTED_OVERFLOW, this);
    return new Outcome(Result.APPLIED, new MountOwnership(destination, revision + 1));
  }
}
