package com.guidedrailways;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class BogiePairContactCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  private static BogiePairContact.Pose pose(
      double x, double y, double z, double yaw, boolean guided) {
    return new BogiePairContact.Pose(
        new Vector3d(x, y, z), new Quaterniond().rotationY(yaw), guided, BogiePairContact.HALF);
  }

  public static void main(String[] args) {
    var a = pose(0, 0, 0, 0, true);
    double length = 2 * BogiePairContact.HALF.z();
    check(
        BogiePairContact.find(a, pose(0, 0, length + .2, 0, true), null) == null,
        "separated vehicles do not receive an extra constraint");
    var touch = BogiePairContact.find(a, pose(0, 0, length, 0, true), null);
    check(
        touch != null && Math.abs(touch.gap()) < 1e-8 && touch.normal().z > .999,
        "straight contact acts longitudinally before the wheels overlap");
    for (double depth : new double[] {.01, .1, .5, 1, 2, 3}) {
      var c = BogiePairContact.find(a, pose(0, 0, length - depth, 0, true), new Vector3d(0, 0, 1));
      check(
          c != null && c.normal().z > .999,
          "deep wand penetration must never flip to a vertical or lateral normal on the same straight rail");
      check(
          c.limit(.01) <= c.separation() + .081,
          "existing deep penetration has bounded solver recovery");
      check(c.limit(.01) >= c.separation(), "recovery requests separation, never attraction");
      var withdrawn =
          BogiePairContact.find(a, pose(0, 0, length - depth + .2, 0, true), c.normal());
      if (withdrawn != null)
        check(
            withdrawn.gap() > c.gap(),
            "withdrawing always reduces penetration, with no re-entrant wheel surface");
    }
    var reversed = BogiePairContact.find(a, pose(0, 0, length, Math.PI, true), null);
    check(
        reversed != null && Math.abs(reversed.minimum() - length) < 1e-8,
        "reversed bogies use the same contact envelope");
    var side = BogiePairContact.find(a, pose(2 * BogiePairContact.HALF.x(), 0, 0, 0, true), null);
    check(
        side != null && side.normal().x > .999,
        "side impact is not mistaken for front/rear contact");
    var top =
        BogiePairContact.find(
            pose(0, 0, 0, 0, false), pose(0, 2 * BogiePairContact.HALF.y(), 0, 0, false), null);
    check(
        top != null && top.normal().y > .999,
        "detached bodies still collide vertically instead of passing through");
    for (int degrees = 0; degrees < 360; degrees += 5) {
      double yaw = Math.toRadians(degrees);
      var q = new Quaterniond().rotationY(yaw);
      var axis = new Vector3d(0, 0, 1).rotate(q);
      var b =
          new BogiePairContact.Pose(
              new Vector3d(axis).mul(length - .05),
              new Quaterniond(q),
              true,
              BogiePairContact.HALF);
      var c =
          BogiePairContact.find(
              new BogiePairContact.Pose(new Vector3d(), q, true, BogiePairContact.HALF), b, null);
      check(
          c != null && c.normal().dot(axis) > .999,
          "same-track contact is invariant under overall yaw");
      check(c.minimum() > 0 && Double.isFinite(c.minimum()), "finite unilateral contact distance");
    }
    System.out.println("BogiePairContactCheck: " + checks + " assertions passed");
  }
}
