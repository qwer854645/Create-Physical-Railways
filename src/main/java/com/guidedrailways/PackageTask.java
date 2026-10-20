package com.guidedrailways;

import java.util.*;

/** Native package instructions skip empty work, but never skip unknown cargo. */
final class PackageTask {
  enum Decision {
    VISIT,
    SKIP,
    UNLOADED,
    NO_TARGET
  }

  static Decision decide(
      boolean delivery, boolean complete, boolean hasPackages, Set<UUID> targets) {
    if (!targets.isEmpty()) return Decision.VISIT;
    if (!complete) return Decision.UNLOADED;
    return delivery && hasPackages ? Decision.NO_TARGET : Decision.SKIP;
  }
}
