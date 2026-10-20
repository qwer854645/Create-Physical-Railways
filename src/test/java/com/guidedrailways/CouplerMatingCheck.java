package com.guidedrailways;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class CouplerMatingCheck {
  private static int checks;

  private static void check(boolean result, String why) {
    checks++;
    if (!result) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    double rest = 18 / 16.0;
    for (int angle = -35; angle <= 35; angle += 5)
      for (int roll = -20; roll <= 20; roll += 5)
        for (double slack : new double[] {-.06, 0, .06}) {
          var a = new Vector3d(3, 5, 7);
          var b = new Vector3d(0, 0, -(rest + slack)).rotateY(Math.toRadians(angle)).add(a);
          var upA = new Vector3d(0, 1, 0).rotateZ(Math.toRadians(roll));
          var upB = new Vector3d(0, 1, 0).rotateX(Math.toRadians(roll / 2.0));
          var first = CouplerMating.head(a, b, upA, upB, rest);
          var second = CouplerMating.head(b, a, upB, upA, rest);
          check(
              Math.abs(first.bearing().distance(second.bearing()) - rest) < 1e-9,
              "draft travel preserves the calibrated jaw spacing through slack and wobble");
          var forwardA = new Vector3d(0, 0, -1).rotate(first.rotation());
          var forwardB = new Vector3d(0, 0, -1).rotate(second.rotation());
          check(
              forwardA.add(forwardB).length() < 1e-9, "both heads have one reciprocal mating axis");
          check(
              new Vector3d(0, 1, 0)
                      .rotate(first.rotation())
                      .distance(new Vector3d(0, 1, 0).rotate(second.rotation()))
                  < 1e-9,
              "roll cannot independently twist the two mating heads");
          check(
              first.rotation().isFinite() && second.rotation().isFinite(),
              "mating frame remains finite");
        }
    for (var kind : new CouplerKind[] {CouplerKind.KNUCKLE, CouplerKind.KNUCKLE_SPLIT})
      check(
          CouplerMechanism.nominalLength(kind, 1.25) == rest,
          "native jaw depth, not tip-to-tip contact, defines locked spacing");
    var large =
        CouplerMating.head(
            new Vector3d(),
            new Vector3d(0, 0, -10),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 1, 0),
            rest);
    check(
        large.bearing().length() <= .125,
        "a stretched pair cannot detach its visual shank from the sleeve");
    check(
        CouplerMating.head(
                new Vector3d(), new Vector3d(), new Vector3d(0, 1, 0), new Vector3d(0, 1, 0), rest)
            .rotation()
            .equals(new Quaterniond()),
        "coincident unloaded anchors have a finite fallback");
    System.out.println("CouplerMatingCheck: " + checks + " assertions passed");
  }
}
