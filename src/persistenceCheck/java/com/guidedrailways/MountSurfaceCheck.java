package com.guidedrailways;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Real Minecraft shape/raycast checks without starting a world or server. */
public final class MountSurfaceCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    int lowered = 0;
    for (var variant : BogeyVariant.values()) {
      for (boolean compact : new boolean[] {false, true}) {
        var shape = MountShapes.get(variant, compact);
        check(shape == MountShapes.get(variant, compact), "platform shape must be cached");
        check(
            !Shapes.joinIsNotEmpty(
                shape.getFaceShape(Direction.UP), Shapes.block(), BooleanOp.NOT_SAME),
            "every platform has a full upward building face");
        check(
            !Shapes.joinIsNotEmpty(shape, Shapes.box(0, 1, 0, 1, 2, 1), BooleanOp.AND),
            "platform geometry cannot obstruct the first block above it");
        for (double x : new double[] {.01, .5, .99})
          for (double z : new double[] {.01, .5, .99}) {
            var hit = shape.clip(new Vec3(x, 2, z), new Vec3(x, -2, z), BlockPos.ZERO);
            check(
                hit != null
                    && hit.getDirection() == Direction.UP
                    && Math.abs(hit.getLocation().y - 1) < 1e-8,
                "centre and edge raycasts reach the platform top");
          }
      }
      check(
          variant.compactMountTop <= variant.mountTop, "compact placement never raises the floor");
      check(
          Math.abs(variant.ride + variant.compactPlateOffset() + .5 - variant.compactMountTop)
              < 1e-8,
          "compact floor height stays on the block grid");
      if (variant.compactMountTop < variant.mountTop) lowered++;
      for (var plate : variant.compactPlate) {
        double radius =
            Math.hypot(
                Math.max(Math.abs(plate[0] - .5), Math.abs(plate[3] - .5)),
                Math.max(Math.abs(plate[2] - .5), Math.abs(plate[5] - .5)));
        double underside = variant.compactMountTop - 1 + plate[1];
        for (var b : BogieCollision.boxes(variant)) {
          double dx = Math.max(0, Math.max(b.x0() - .5, .5 - b.x1()));
          double dz = Math.max(0, Math.max(b.z0() - .5, .5 - b.z1()));
          if (dx * dx + dz * dz < radius * radius - 1e-8)
            check(
                variant.ride - .5 + b.y1() < underside - 1e-4,
                "platform underside clears the native animation envelope through any yaw: "
                    + variant.id);
        }
      }
    }
    check(lowered == 39, "39 native profiles use a lower mounting floor");
    System.out.println(
        "MountSurfaceCheck: "
            + checks
            + " top face, build-space, raycast and animation-clearance checks passed");
  }
}
