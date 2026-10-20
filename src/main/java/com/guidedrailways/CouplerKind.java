package com.guidedrailways;

/**
 * Functional hardware types; paint, material and mounting variant do not change the coupling
 * mechanism.
 */
public enum CouplerKind {
  STANDARD,
  LINK,
  LINKLESS,
  KNUCKLE,
  KNUCKLE_SPLIT,
  SCREWLINK;

  public static CouplerKind railway(String style) {
    return switch (style) {
      case "link" -> LINK;
      case "linkless" -> LINKLESS;
      case "knuckle" -> KNUCKLE;
      case "knuckle_split" -> KNUCKLE_SPLIT;
      case "screwlink" -> SCREWLINK;
      default -> null;
    };
  }

  public static boolean compatible(CouplerKind a, CouplerKind b) {
    return a != null && a == b;
  }
}
