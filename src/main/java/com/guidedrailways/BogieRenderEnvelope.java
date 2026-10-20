package com.guidedrailways;

/** Model-wide animation envelope, computed once per style rather than once per placed vehicle. */
final class BogieRenderEnvelope {
  record Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {}

  private static final java.util.Map<BogeyVariant, Bounds> CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

  static Bounds get(BogeyVariant variant) {
    return CACHE.computeIfAbsent(
        variant,
        key -> {
          double x0 = 0, y0 = 0, z0 = 0, x1 = 1, y1 = 1, z1 = 1;
          for (var b : BogieCollision.boxes(key)) {
            x0 = Math.min(x0, b.x0());
            y0 = Math.min(y0, b.y0());
            z0 = Math.min(z0, b.z0());
            x1 = Math.max(x1, b.x1());
            y1 = Math.max(y1, b.y1());
            z1 = Math.max(z1, b.z1());
          }
          return new Bounds(
              x0 - 1.0 / 16, y0 - 1.0 / 16, z0 - 1.0 / 16, x1 + 1.0 / 16, y1 + 1.0 / 16,
              z1 + 1.0 / 16);
        });
  }
}
