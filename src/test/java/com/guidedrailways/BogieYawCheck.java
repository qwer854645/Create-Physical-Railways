package com.guidedrailways;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class BogieYawCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    for (int i = 0; i < 180; i++) {
      var before = new Quaterniond().rotateYXZ(i * .17, .2 * Math.sin(i), .1 * Math.cos(i));
      var after = new Quaterniond().rotateYXZ(-i * .13, -.13 * Math.cos(i), .23 * Math.sin(i));
      for (double localYaw : new double[] {0, Math.PI, .7, -1.4}) {
        var local = new Quaterniond().rotateY(localYaw);
        var world = new Quaterniond(before).mul(local);
        var moved = BogieYawModel.transfer(before, local, after);
        check(
            Math.abs(new Quaterniond(after).mul(moved).dot(world)) > 1 - 1e-10,
            "gluing into a differently oriented body preserves the physical neutral direction");
        var returned = BogieYawModel.transfer(after, moved, before);
        check(
            Math.abs(returned.dot(local)) > 1 - 1e-10,
            "splitting back out preserves the mounting reference without accumulated drift");
      }
      for (double yaw : new double[] {-.4, 0, .4, Math.PI - .4, Math.PI, Math.PI + .4}) {
        var bogie = new Quaterniond(before).rotateY(yaw);
        var reference = BogieYawModel.reference(before, bogie);
        var difference =
            new Vector3d(0, 0, 1)
                .rotate(bogie)
                .rotate(new Quaterniond(before).mul(reference).conjugate());
        check(
            Math.abs(Math.atan2(difference.x, difference.z)) < .41,
            "old reversed bogies recover the nearer longitudinal direction rather than being forced through 180 degrees");
      }
    }
    for (int count = 1; count <= 16; count++) {
      var spring = BogieYawModel.spring(count);
      check(
          spring.stiffness() > 0 && spring.damping() > 0 && spring.maxTorque() > 0,
          "native yaw motor has nonzero centering and damping with a finite positive cap");
      check(
          Math.abs(spring.stiffness() * count - 12) < 1e-12
              && Math.abs(spring.maxTorque() * count - 120) < 1e-12,
          "multiple bogies share the weak spring budget instead of multiplying stiffness on one body");
    }
    System.out.println("BogieYawCheck: " + checks + " assertions passed");
  }
}
