package com.guidedrailways;

import java.util.function.DoubleFunction;
import org.joml.Vector3d;

/** CPU-only geometry regression: no Minecraft world or dedicated server. */
public final class PathGeometryCheck {
  static int checks;

  static void check(boolean ok, String text) {
    checks++;
    if (!ok) throw new AssertionError(text);
  }

  static Vector3d bezier(double t, double radius) {
    double k = .5522847498307936, u = 1 - t;
    return new Vector3d(
        radius * (u * u * u + 3 * u * u * t + 3 * u * t * t * k),
        0,
        radius * (3 * u * u * t * k + 3 * u * t * t + t * t * t));
  }

  static Vector3d derivative(double t, double radius) {
    double k = .5522847498307936, u = 1 - t;
    return new Vector3d(
            radius * (6 * u * t * (k - 1) - 3 * t * t * k),
            0,
            radius * (3 * u * u * k + 6 * u * t * (1 - k)))
        .normalize();
  }

  public static void main(String[] args) {
    for (double radius : new double[] {4, 16, 64, 256}) {
      DoubleFunction<Vector3d> curve = t -> bezier(t, radius);
      for (int i = 0; i <= 100; i++) {
        double t = i / 100.0;
        var tangent = PathGeometry.tangent(curve, t);
        check(
            tangent.dot(derivative(t, radius)) > .999999,
            "tangent must follow projected parameter, including curve exit");
        var reverse = PathGeometry.tangent(s -> curve.apply(1 - s), 1 - t);
        check(tangent.dot(reverse) < -.999999, "reverse parameterization must reverse direction");
      }
      double length = radius * Math.PI / 2;
      var old = PathGeometry.tangent(curve, 1 / length);
      var fixed = PathGeometry.tangent(curve, 1);
      check(
          old.dot(fixed) < .5,
          "fixture reproduces wrong distance/fraction API usage at curve exit");
      check(
          fixed.dot(new Vector3d(-1, 0, 0)) > .999999,
          "exit direction is horizontal left, not entry direction");
      var end = curve.apply(1);
      check(
          PathGeometry.beyondEndpoint(1, end, new Vector3d(end).fma(.2, fixed), fixed, .08),
          "forward overrun releases finite rail");
      check(
          !PathGeometry.beyondEndpoint(1, end, new Vector3d(end).fma(-.2, fixed), fixed, .08),
          "inside curve is not beyond exit");
      var start = curve.apply(0);
      var first = PathGeometry.tangent(curve, 0);
      check(
          PathGeometry.beyondEndpoint(0, start, new Vector3d(start).fma(-.2, first), first, .08),
          "reverse overrun releases finite rail");
    }
    var uphill = PathGeometry.tangent(t -> new Vector3d(3 * t, 4 * t, 0), .5);
    check(uphill.distance(new Vector3d(.6, .8, 0)) < 1e-8, "slope direction");
    System.out.println("PathGeometryCheck: " + checks + " assertions passed");
  }
}
