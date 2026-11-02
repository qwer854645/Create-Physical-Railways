package com.guidedrailways;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Rays constructed with the renderer's forward matrix must survive inverse model picking. */
public final class ConsoleInteractionCheck {
  private static int checks;
  private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
  private static Vec3 point(Matrix4d m, double x, double y, double z) {
    var p = m.transformPosition(new Vector3d(x, y, z)); return new Vec3(p.x, p.y, p.z);
  }
  public static void main(String[] args) {
    for (var facing : Direction.Plane.HORIZONTAL) {
      var plate = new Matrix4d().translate(.5, 0, .5)
          .rotateY(-Math.toRadians((facing.toYRot() + 180) % 360)).translate(-.5, 0, -.5)
          .translate(.5, .625, .5).rotateX(Math.PI / 8).translate(-.5, -.625, -.5);
      for (int n = -DriverModel.BRAKE_NOTCHES; n <= DriverModel.MAX_NOTCH; n++) {
        double lever = DriverModel.lever(n);
        var handle = new Matrix4d(plate).translate(11 / 16d, 12 / 16d, 7.5 / 16d)
            .rotateX(Math.toRadians(-36 * lever)).translate(-11 / 16d, -12 / 16d, -7.5 / 16d);
        check(ConsoleInteraction.pick(point(handle, 11 / 16d, 2, 7.5 / 16d),
            point(handle, 11 / 16d, 14 / 16d, 7.5 / 16d), facing, lever) == ConsoleInteraction.Part.LEVER,
            "moving handle hit: " + facing + " notch=" + n);
        check(ConsoleInteraction.pick(point(plate, 5 / 16d, 2, 12 / 16d),
            point(plate, 5 / 16d, 11 / 16d, 12 / 16d), facing, lever) == ConsoleInteraction.Part.EMERGENCY,
            "button remains separate from handle: " + facing + " notch=" + n);
      }
      check(ConsoleInteraction.pick(point(plate, 5 / 16d, -2, 12 / 16d),
          point(plate, 5 / 16d, 1, 12 / 16d), facing, 0) == ConsoleInteraction.Part.NONE,
          "cannot press a button through the pedestal");
      check(ConsoleInteraction.pick(point(plate, 0, 2, 0), point(plate, 0, -1, 0), facing, 0)
          == ConsoleInteraction.Part.NONE, "empty coarse selection space does nothing");
    }
    System.out.println("ConsoleInteractionCheck: " + checks + " moving-part, facing and occlusion checks passed");
  }
}
