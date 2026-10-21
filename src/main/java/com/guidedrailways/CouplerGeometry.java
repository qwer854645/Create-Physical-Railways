package com.guidedrailways;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Pure initial engagement geometry. There is no scripted pulling force. */
public final class CouplerGeometry {
  /** Standard drawbar half-stroke and manual coupling selection limits. */
  public static final double HALF_STROKE = .1, MAX_GAP = 2.0, MAX_HEIGHT = 1.0;

  public static final double MAX_RELATIVE_ACCELERATION = 24;

  public record Spring(double stiffness, double damping, double maxForce) {}

  private CouplerGeometry() {}

  public static Quaterniond engage(
      Vector3dc a,
      Vector3dc facingA,
      Vector3dc upA,
      Vector3dc b,
      Vector3dc facingB,
      Vector3dc upB) {
    if (!a.isFinite()
        || !b.isFinite()
        || !facingA.isFinite()
        || !facingB.isFinite()
        || facingA.lengthSquared() < .5
        || facingB.lengthSquared() < .5)
      throw new IllegalArgumentException("message.guidedrailways.coupler.invalid_pose");
    var fa = new Vector3d(facingA).normalize();
    var fb = new Vector3d(facingB).normalize();
    var delta = new Vector3d(b).sub(a);
    if (delta.length() > MAX_GAP)
      throw new IllegalArgumentException("message.guidedrailways.coupler.too_far");
    var up = new Vector3d(upA).add(upB);
    if (!up.isFinite() || up.lengthSquared() < .1)
      throw new IllegalArgumentException("message.guidedrailways.coupler.align");
    up.normalize();
    if (Math.abs(delta.dot(up)) > MAX_HEIGHT)
      throw new IllegalArgumentException("message.guidedrailways.coupler.height");
    // Manual coupling may bridge a curve or sideways offset; only derive the initial axis.
    var x = new Vector3d(delta);
    if (x.lengthSquared() < 1e-8) x.set(fa).sub(fb);
    if (x.lengthSquared() < 1e-8) x.set(fa);
    x.normalize();
    if (new Vector3d(x).cross(up).lengthSquared() < .1)
      up.set(Math.abs(x.y) < .9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0));
    var z = new Vector3d(x).cross(up);
    if (z.lengthSquared() < .1)
      throw new IllegalArgumentException("message.guidedrailways.coupler.invalid_pose");
    z.normalize();
    var y = new Vector3d(z).cross(x).normalize();
    return new Quaterniond().setFromNormalized(new Matrix3d(x, y, z));
  }

  /** World X follows the current drawbar, with no stored yaw/pitch/roll reference. */
  public static Quaterniond distanceFrame(Vector3dc a, Vector3dc b) {
    var d = new Vector3d(b).sub(a);
    if (!d.isFinite()) throw new IllegalArgumentException("invalid drawbar endpoints");
    return d.lengthSquared() < 1e-12
        ? new Quaterniond()
        : new Quaterniond().rotationTo(new Vector3d(1, 0, 0), d.normalize());
  }

  public static double extension(Vector3dc a, Vector3dc b, double restLength) {
    return a.distance(b) - restLength;
  }

  public static double nominalLength(double reachA, double reachB) {
    if (!Double.isFinite(reachA) || !Double.isFinite(reachB) || reachA <= 0 || reachB <= 0)
      throw new IllegalArgumentException("invalid coupler reach");
    return reachA + reachB;
  }

  public static boolean slowEnough(Vector3dc va, Vector3dc vb, Vector3dc wa, Vector3dc wb) {
    return va.isFinite()
        && vb.isFinite()
        && wa.isFinite()
        && wb.isFinite()
        && va.lengthSquared() <= 4
        && vb.lengthSquared() <= 4
        && va.distanceSquared(vb) <= 4
        && wa.lengthSquared() <= 2.25
        && wb.lengthSquared() <= 2.25;
  }

  public static Spring spring(double ma, double mb) {
    if (!Double.isFinite(ma) || !Double.isFinite(mb) || ma <= 0 || mb <= 0)
      throw new IllegalArgumentException("message.guidedrailways.body.invalid_mass");
    double reduced = 1 / (1 / ma + 1 / mb), omega = 2 * Math.PI * 2;
    return new Spring(
        reduced * omega * omega, 2 * reduced * omega, reduced * MAX_RELATIVE_ACCELERATION);
  }
}
