package com.guidedrailways;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A pair shares its mating axis and up vector, rather than independently aiming two overlapping
 * heads.
 */
public final class CouplerMating {
  public record Pose(Vector3d bearing, Quaterniond rotation) {}

  public static Pose head(
      Vector3d own, Vector3d peer, Vector3d upOwn, Vector3d upPeer, double length) {
    var direction = new Vector3d(peer).sub(own);
    double distance = direction.length();
    if (distance < 1e-8) return new Pose(new Vector3d(own), new Quaterniond());
    direction.div(distance);
    var up = new Vector3d(upOwn).add(upPeer);
    up.fma(-up.dot(direction), direction);
    if (up.lengthSquared() < 1e-8) {
      up.set(0, 1, 0).fma(-direction.y, direction);
      if (up.lengthSquared() < 1e-8) up.set(1, 0, 0);
    }
    up.normalize();
    var z = new Vector3d(direction).negate();
    var x = new Vector3d(up).cross(z).normalize();
    var y = new Vector3d(z).cross(x);
    // Small draft-gear travel is shown inside the stationary sleeve, not as overlapping jaws.
    double stroke = Math.clamp((distance - length) / 2, -.125, .125);
    return new Pose(
        new Vector3d(own).fma(stroke, direction),
        new Quaterniond().setFromNormalized(new Matrix3d(x, y, z)));
  }

  private CouplerMating() {}
}
