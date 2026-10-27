package com.guidedrailways;

import java.util.Set;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Offline checks for the two conventions that only ever showed up in the running game. */
public final class FramesCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static final double PLOT_X = 20481031.5, PLOT_Y = 127.5, PLOT_Z = 20489223.5;

  public static void main(String[] args) {
    var origin = new Vector3d();
    var rotation = new Quaterniond();
    var position = new Vector3d(0, 0, 0);
    var point = new Vector3d(3, 2, 1);
    check(
        Frames.toWorld(point, rotation, origin, position).equals(point, 1e-12),
        "identity pose at the world origin leaves a plot point untouched");

    // Real scale: a body plotted 20 million blocks away but sitting next to the player.
    var rotationPoint = new Vector3d(PLOT_X, PLOT_Y, PLOT_Z);
    var bodyPosition = new Vector3d(50, -58, 7);
    var yaw = new Quaterniond().rotateY(Math.PI / 2);
    var atOrigin = Frames.toWorld(rotationPoint, yaw, rotationPoint, bodyPosition);
    check(
        atOrigin.equals(bodyPosition, 1e-9),
        "the rotation point maps onto the body's world position, whatever the rotation");
    var offset = new Vector3d(rotationPoint).add(1, 0, 0);
    var mapped = Frames.toWorld(offset, yaw, rotationPoint, bodyPosition);
    check(
        mapped.equals(new Vector3d(0, 0, -1).add(bodyPosition), 1e-9),
        "an offset rotates about the plot origin, not about the world origin");

    // The failure this guard exists for: omitting the rotation point rotates an absolute coordinate.
    var withoutRotationPoint = Frames.toWorld(offset, yaw, origin, bodyPosition);
    double drift = withoutRotationPoint.distance(bodyPosition);
    check(
        drift > 1e7,
        "dropping the rotation point throws the part millions of blocks away, so a caller that"
            + " forgets it cannot pass as a small inaccuracy: "
            + drift);

    for (int supports = 1; supports <= 4; supports++) {
      Set<BearingLock.Axis> rigid = BearingLock.locked(supports);
      check(
          rigid.equals(Set.of(BearingLock.Axis.ROLL, BearingLock.Axis.PITCH)),
          "roll and pitch stay rigid with " + supports + " bearings");
      check(
          !rigid.contains(BearingLock.Axis.YAW),
          "yaw is never locked with " + supports + " bearings, or the bogies fight each other");
      check(
          Math.abs(BearingLock.share(supports) * supports - 1) < 1e-12,
          "the centring budget is shared, not multiplied, with " + supports + " bearings");
    }
    check(
        BearingLock.share(0) == 1,
        "a carrier without a registered bearing still gets the whole budget");
    System.out.println("FramesCheck: " + checks + " frame and bearing-rigidity checks passed");
  }
}
