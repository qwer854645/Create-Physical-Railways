package com.guidedrailways;

import java.util.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** A smooth pair envelope only; block/entity collision geometry remains detailed. */
public final class BogiePairContact {
  public static final double SKIN = .12;

  public record Pose(Vector3d center, Quaterniond rotation, boolean guided, Vector3dc half) {
    public Pose(Vector3d center, Quaterniond rotation, boolean guided) {
      this(center, rotation, guided, HALF);
    }
  }

  public record Contact(Vector3d normal, double separation, double minimum) {
    public double gap() {
      return separation - minimum;
    }

    public double limit(double dt) {
      return gap() < -.08 ? Math.min(minimum, separation + dt) : minimum;
    }
  }

  public static final Vector3dc OFFSET;
  public static final Vector3dc HALF;

  static {
    var low = new Vector3d(Double.POSITIVE_INFINITY);
    var high = new Vector3d(Double.NEGATIVE_INFINITY);
    for (var b : BogieCollision.BOXES) {
      low.min(new Vector3d(b.x0(), b.y0(), b.z0()));
      high.max(new Vector3d(b.x1(), b.y1(), b.z1()));
    }
    OFFSET = new Vector3d(low).add(high).mul(.5).sub(.5, .5, .5);
    HALF = new Vector3d(high).sub(low).mul(.5);
  }

  private record Bounds(Vector3dc offset, Vector3dc half) {}

  public static Vector3dc offset(RailGauge gauge) {
    return gauge == RailGauge.STANDARD ? OFFSET : NATIVE.get(BogeyVariant.defaultFor(gauge)).offset;
  }

  public static Vector3dc half(RailGauge gauge) {
    return gauge == RailGauge.STANDARD ? HALF : NATIVE.get(BogeyVariant.defaultFor(gauge)).half;
  }

  private static final Map<BogeyVariant, Bounds> NATIVE = new HashMap<>();

  static {
    for (var v : BogeyVariant.values())
      if (v != BogeyVariant.DEFAULT) {
        var lo = new Vector3d(Double.POSITIVE_INFINITY);
        var hi = new Vector3d(Double.NEGATIVE_INFINITY);
        for (var b : BogieCollision.boxes(v)) {
          lo.min(new Vector3d(b.x0(), b.y0(), b.z0()));
          hi.max(new Vector3d(b.x1(), b.y1(), b.z1()));
        }
        NATIVE.put(
            v,
            new Bounds(
                new Vector3d(lo).add(hi).mul(.5).sub(.5, .5, .5),
                new Vector3d(hi).sub(lo).mul(.5)));
      }
  }

  public static Vector3dc offset(BogeyVariant variant) {
    return variant == BogeyVariant.DEFAULT ? OFFSET : NATIVE.get(variant).offset;
  }

  public static Vector3dc half(BogeyVariant variant) {
    return variant == BogeyVariant.DEFAULT ? HALF : NATIVE.get(variant).half;
  }

  private static Vector3d[] axes(Quaterniond q) {
    return new Vector3d[] {
      new Vector3d(1, 0, 0).rotate(q),
      new Vector3d(0, 1, 0).rotate(q),
      new Vector3d(0, 0, 1).rotate(q)
    };
  }

  private static double radius(Vector3d n, Vector3d[] axes, Vector3dc half) {
    return Math.abs(n.dot(axes[0])) * half.x()
        + Math.abs(n.dot(axes[1])) * half.y()
        + Math.abs(n.dot(axes[2])) * half.z();
  }

  public static Contact find(Pose a, Pose b, Vector3dc previous) {
    if (!a.center.isFinite()
        || !b.center.isFinite()
        || !a.rotation.isFinite()
        || !b.rotation.isFinite()) return null;
    var aa = axes(a.rotation);
    var bb = axes(b.rotation);
    var delta = new Vector3d(b.center).sub(a.center);
    var candidates = new ArrayList<Vector3d>();
    Collections.addAll(candidates, aa);
    Collections.addAll(candidates, bb);
    for (var x : aa)
      for (var y : bb) {
        var n = new Vector3d(x).cross(y);
        if (n.lengthSquared() > 1e-10) candidates.add(n.normalize());
      }
    Vector3d normal = null;
    double best = -Double.MAX_VALUE;
    for (var axis : candidates) {
      double gap = Math.abs(delta.dot(axis)) - radius(axis, aa, a.half) - radius(axis, bb, b.half);
      if (gap > best) {
        best = gap;
        normal = new Vector3d(axis);
      }
    }
    if (best > SKIN) return null;
    if (a.guided
        && b.guided
        && Math.abs(aa[2].dot(bb[2])) > .985
        && Math.abs(delta.dot(aa[0])) < .25
        && Math.abs(delta.dot(aa[1])) < .25) {
      // On the same straight track, contact must resist longitudinal motion, not fight
      // the lateral/vertical guides after a deep wand-driven overlap.
      normal = new Vector3d(aa[2]);
    } else if (previous != null && previous.isFinite() && previous.lengthSquared() > .9) {
      var n = new Vector3d(previous).normalize();
      double gap = Math.abs(delta.dot(n)) - radius(n, aa, a.half) - radius(n, bb, b.half);
      if (gap >= best - .04) normal = n;
    }
    if (previous != null && Math.abs(normal.dot(previous)) > .9) {
      if (normal.dot(previous) < 0) normal.negate();
    } else if (delta.dot(normal) < 0) normal.negate();
    double separation = delta.dot(normal),
        minimum = radius(normal, aa, a.half) + radius(normal, bb, b.half);
    return new Contact(normal, separation, minimum);
  }
}
