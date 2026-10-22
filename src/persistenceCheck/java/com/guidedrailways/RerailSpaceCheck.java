package com.guidedrailways;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.tree.*;

/** Real Sable SAT against payload, thin shapes and rotated obstacles, without loading a world. */
public final class RerailSpaceCheck {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static RerailSpace.Box box(
      double x0, double y0, double z0, double x1, double y1, double z1) {
    return RerailSpace.box(new AABB(x0, y0, z0, x1, y1, z1), new Pose3d(), false);
  }

  public static void main(String[] args) throws Exception {
    var overlap = new RerailSpace.Overlap();
    var payload = box(-1, 2, -2, 1, 3, 2);
    check(
        overlap.test(payload, box(-2, 2.5, -3, 2, 3.5, 3)),
        "ceiling blocks payload above clear bogies");
    check(overlap.test(payload, box(.9, 1, -4, 1.1, 4, 4)), "thin wall is a real obstruction");
    check(!overlap.test(payload, box(1, 2, -2, 2, 3, 2)), "touching faces are permitted");
    check(
        !overlap.test(payload, box(1.01, 2, -2, 2, 3, 2)),
        "nearby body with clearance is permitted");
    check(overlap.test(payload, box(.8, 2, -2, 2, 3, 2)), "overlapping other body is rejected");
    var pose = new Pose3d();
    pose.rotationPoint().set(100.5, 64.5, 100.5);
    pose.position().set(0, 3, 0);
    pose.orientation().rotationY(Math.PI / 4);
    var rotated = RerailSpace.box(new AABB(100, 64, 98, 101, 65, 103), pose, false);
    check(
        overlap.test(rotated, box(-.1, 2.9, -.1, .1, 3.1, .1)),
        "COM-relative rotated payload hits wall");
    check(
        !overlap.test(rotated, box(-2, 2.9, 1.8, -1.8, 3.1, 2)),
        "AABB overlap alone cannot reject a rotated gap");
    var hollowLeft = box(-2, 2, -2, -.6, 4, 2);
    var hollowRight = box(.6, 2, -2, 2, 4, 2);
    var passage = box(-.5, 2, -1, .5, 4, 1);
    check(
        !overlap.test(hollowLeft, passage) && !overlap.test(hollowRight, passage),
        "empty space between actual shapes stays usable");
    pose = new Pose3d();
    pose.scale().set(2, 1, 1);
    check(
        overlap.test(
            RerailSpace.box(new AABB(0, 0, 0, 1, 1, 1), pose, false), box(1.5, 0, 0, 2.5, 1, 1)),
        "scaled body geometry is included");
    var ground = box(-100, -1, -100, 100, 0, 100);
    for (var variant : BogeyVariant.values()) {
      if (variant.gauge == RailGauge.MONORAIL) continue;
      pose = new Pose3d();
      pose.rotationPoint().set(.5, .5, .5);
      pose.position().set(0, variant.ride + .125, 0);
      for (var cell : BogieCollision.cells(variant))
        for (var b : cell.boxes()) {
          var shape =
              new AABB(
                  b.x0() + cell.x(),
                  b.y0() + cell.y(),
                  b.z0() + cell.z(),
                  b.x1() + cell.x(),
                  b.y1() + cell.y(),
                  b.z1() + cell.z());
          check(
              !overlap.test(RerailSpace.box(shape, pose, false), ground),
              "ordinary rail placement clears ground: " + variant.id + "/" + variant.size);
        }
    }
    var code = new ClassNode();
    try (var input =
        RerailSpaceCheck.class.getResourceAsStream("/com/guidedrailways/VehiclePlacement.class")) {
      new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(input)).accept(code, 0);
    }
    var method =
        code.methods.stream()
            .filter(m -> m.name.equals("reraileCarriage"))
            .findFirst()
            .orElseThrow();
    int preflight = -1, commit = -1;
    for (var instruction : method.instructions)
      if (instruction instanceof MethodInsnNode call) {
        if (call.owner.equals("com/guidedrailways/RerailSpace") && call.name.equals("check"))
          preflight = method.instructions.indexOf(call);
        if (call.owner.equals("com/guidedrailways/MoveBatch") && call.name.equals("commit"))
          commit = method.instructions.indexOf(call);
      }
    check(
        preflight >= 0 && commit > preflight,
        "whole-body preflight precedes all transactional moves");
    System.out.println(
        "RerailSpaceCheck: " + checks + " geometry and preflight boundary checks passed");
  }
}
