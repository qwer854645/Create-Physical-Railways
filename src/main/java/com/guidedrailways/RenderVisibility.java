package com.guidedrailways;

/**
 * Use distance to the model envelope, so a long model's visible nose does not vanish with its
 * centre.
 */
final class RenderVisibility {
  static boolean within(
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      double x,
      double y,
      double z,
      double range) {
    double dx = Math.max(0, Math.max(x0 - x, x - x1)),
        dy = Math.max(0, Math.max(y0 - y, y - y1)),
        dz = Math.max(0, Math.max(z0 - z, z - z1));
    return dx * dx + dy * dy + dz * dz < range * range;
  }
}
