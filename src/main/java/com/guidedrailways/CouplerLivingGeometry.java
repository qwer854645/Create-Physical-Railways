package com.guidedrailways;

import java.util.*;
import org.joml.Vector3d;

/**
 * Small upright boxes cover the rotated visual hardware without a large diagonal bounding prism.
 */
public final class CouplerLivingGeometry {
  public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
    public boolean contains(Vector3d p) {
      return p.x >= x0 - 1e-8
          && p.x <= x1 + 1e-8
          && p.y >= y0 - 1e-8
          && p.y <= y1 + 1e-8
          && p.z >= z0 - 1e-8
          && p.z <= z1 + 1e-8;
    }
  }

  public static List<Box> boxes(CouplerVisual.Layout layout) {
    var boxes = new ArrayList<Box>();
    for (var b : layout.boxes()) {
      int count = Math.clamp((int) Math.ceil((b.z1() - b.z0()) / .125), 1, 32);
      for (int i = 0; i < count; i++) {
        double z0 = b.z0() + (b.z1() - b.z0()) * i / count,
            z1 = b.z0() + (b.z1() - b.z0()) * (i + 1) / count;
        var min = new Vector3d(Double.POSITIVE_INFINITY);
        var max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (double x : new double[] {b.x0(), b.x1()})
          for (double y : new double[] {b.y0(), b.y1()})
            for (double z : new double[] {z0, z1}) {
              var p = new Vector3d(x, y, z).rotate(layout.rotation()).add(layout.pivot());
              min.min(p);
              max.max(p);
            }
        boxes.add(new Box(min.x, min.y, min.z, max.x, max.y, max.z));
      }
    }
    return List.copyOf(boxes);
  }
}
