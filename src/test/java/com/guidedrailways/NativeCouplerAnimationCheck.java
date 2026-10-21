package com.guidedrailways;

import com.guidedrailways.NativeCouplerAnimation.Part;
import java.util.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Check the common transform against the former renderer's point-by-point operations. */
public final class NativeCouplerAnimationCheck {
  private static int checks;

  private static void near(Vector3d a, Vector3d b, String why) {
    checks++;
    if (a.distance(b) > 1e-9) throw new AssertionError(why + " " + a + " / " + b);
  }

  private static Vector3d around(Vector3d point, Vector3d pivot, Quaterniond rotation) {
    return new Vector3d(point).sub(pivot).rotate(rotation).add(pivot);
  }

  public static void main(String[] args) {
    var workspace = new NativeCouplerAnimation.Workspace();
    for (var kind : CouplerKind.values())
      if (kind != CouplerKind.STANDARD)
        for (double shift : new double[] {0, -3 / 16.0})
          for (double p : new double[] {0, .15, .35, .65, 1})
            for (boolean valid : new boolean[] {false, true})
              for (boolean active : new boolean[] {false, true}) {
                var target = new Vector3d(.65, .6, -.6 + shift);
                var peerUp = new Vector3d(.04, 1, .03).normalize();
                var actual =
                    NativeCouplerAnimation.transforms(
                        kind, p, .8, shift, valid, active, target, peerUp, workspace);
                var motion = CouplerMechanism.motion(p);
                for (var part : Part.values())
                  for (var input : List.of(new Vector3d(.3, .2, .1), new Vector3d(.7, .8, .9))) {
                    var expected = new Vector3d(input);
                    if (part == Part.JAW)
                      expected =
                          around(
                              expected,
                              new Vector3d(11 / 16.0, .5, 5 / 16.0 + shift),
                              new Quaterniond().rotateY(Math.toRadians(48) * motion.jawOpen()));
                    if (part == Part.PIN)
                      expected.add(0, .18 * Math.clamp(motion.pinLift(), 0, 1), 0);
                    if (part == Part.LINK) {
                      var pivot = new Vector3d(.5, .5, .5 + shift);
                      double insert = motion.linkInsertion();
                      if (active) {
                        var centre =
                            new Vector3d(
                                .5, 7.653284439003997 / 16, 6.270598714406038 / 16 + shift);
                        var destination = new Vector3d(pivot).add(target).mul(.5);
                        var rotation =
                            new Quaterniond()
                                .rotationTo(
                                    new Vector3d(0, 0, -1),
                                    new Vector3d(target).sub(pivot).normalize())
                                .rotateX(Math.toRadians(22.5));
                        expected
                            .sub(centre)
                            .rotate(new Quaterniond().slerp(rotation, insert))
                            .add(new Vector3d(centre).lerp(destination, insert));
                      } else if (valid)
                        expected =
                            around(
                                expected, pivot, new Quaterniond().rotateX(-Math.PI / 2 * insert));
                    }
                    if (part == Part.RIGGING || part == Part.SCREW) {
                      if (part == Part.SCREW && active)
                        expected =
                            around(
                                expected,
                                new Vector3d(.5, .22, .75 + shift),
                                new Quaterniond().rotateY(Math.PI * 2 * motion.screwTurns()));
                      if (active)
                        expected =
                            around(
                                expected,
                                new Vector3d(.5, .5, .75 + shift),
                                new Quaterniond()
                                    .slerp(
                                        new Quaterniond()
                                            .rotationTo(
                                                new Vector3d(0, -1, 0),
                                                new Vector3d(target)
                                                    .sub(.5, .5, .75 + shift)
                                                    .normalize()),
                                        Math.clamp(p / .35, 0, 1)));
                    }
                    if (part != Part.MOUNT
                        && (kind == CouplerKind.KNUCKLE || kind == CouplerKind.KNUCKLE_SPLIT)
                        && valid
                        && p > 0) {
                      var bearing = new Vector3d(.5, .5, .875 + shift);
                      var mating =
                          CouplerMating.head(
                              bearing,
                              target,
                              new Vector3d(0, 1, 0),
                              peerUp,
                              CouplerMechanism.nominalLength(kind, 0));
                      double blend = Math.min(p, .8);
                      expected =
                          around(
                                  expected,
                                  bearing,
                                  new Quaterniond().slerp(mating.rotation(), blend))
                              .add(new Vector3d(mating.bearing()).sub(bearing).mul(blend));
                    }
                    near(
                        actual.get(part).transformPosition(new Vector3d(input)),
                        expected,
                        "render/collision transform parity " + kind + " " + part);
                  }
              }
    System.out.println(
        "NativeCouplerAnimationCheck: "
            + checks
            + " render/collision point-transform comparisons passed");
  }
}
