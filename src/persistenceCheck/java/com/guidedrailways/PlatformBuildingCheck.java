package com.guidedrailways;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Reproduce the native full-cell false positive using real Sable SAT and captured bogie cells. */
public final class PlatformBuildingCheck {
  private static int checks;

  private static void check(boolean result, String message) {
    checks++;
    if (!result) throw new AssertionError(message);
  }

  private static org.objectweb.asm.tree.ClassNode bytecode(String path) throws java.io.IOException {
    var node = new org.objectweb.asm.tree.ClassNode();
    try (var stream = PlatformBuildingCheck.class.getResourceAsStream("/" + path + ".class")) {
      new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static int priority(org.objectweb.asm.tree.ClassNode node) {
    var annotation =
        node.invisibleAnnotations.stream()
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
            .findFirst()
            .orElseThrow();
    int index = annotation.values.indexOf("priority");
    return index >= 0 ? (int) annotation.values.get(index + 1) : 1000;
  }

  private static void hookContract() throws java.io.IOException {
    var upstream = bytecode("dev/ryanhcode/sable/mixin/block_placement/BlockPlaceContextMixin");
    var ours = bytecode("com/guidedrailways/mixin/PlatformBuildingMixin");
    check(
        priority(ours) > priority(upstream),
        "injection into a merged method needs higher Mixin priority");
    var method =
        upstream.methods.stream()
            .filter(m -> m.name.equals("sable$intersectBlocks"))
            .findFirst()
            .orElseThrow();
    int hits = 0;
    for (var insn : method.instructions) {
      if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
          && call.owner.equals("org/joml/Vector3d")
          && call.name.equals("lengthSquared")
          && call.desc.equals("()D")) {
        hits++;
        int at = method.instructions.indexOf(insn);
        check(
            method.localVariables.stream()
                    .filter(
                        v ->
                            v.desc.equals("Lnet/minecraft/core/BlockPos;")
                                && method.instructions.indexOf(v.start) <= at
                                && method.instructions.indexOf(v.end) > at)
                    .count()
                == 1,
            "the loop cell local is unambiguous at the injection point");
      }
    }
    check(hits == 1, "placement overlap has exactly one matching injection point");
    var args = org.objectweb.asm.Type.getArgumentTypes(method.desc);
    check(
        args[1].getInternalName().equals("dev/ryanhcode/sable/sublevel/SubLevel")
            && args[4].getInternalName().equals("org/joml/Vector3d")
            && args[5].getInternalName().equals("org/joml/Quaterniond"),
        "body and candidate pose locals match the release helper arguments");
  }

  public static void main(String[] args) throws Exception {
    hookContract();
    var pose = new Pose3d();
    pose.position().set(0, .4714599, 0);
    var rotation = new Quaterniond();
    var candidate = new Vector3d(.5, 1.5, .5);
    var scratch = new LevelReusedVectors();
    var fullCandidate = new OrientedBoundingBox3d(candidate, new Vector3d(1), rotation, scratch);
    var fullCell =
        new OrientedBoundingBox3d(
            new Vector3d(.5, .9714599, .5), new Vector3d(1), rotation, scratch);
    check(
        OrientedBoundingBox3d.sat(fullCandidate, fullCell).lengthSquared() > .05,
        "Sable whole-cell placement rejects this fixture");
    check(
        !PlacementGeometry.intersects(
            Shapes.box(0, 0, 0, 1, .125, 1), BlockPos.ZERO, pose, candidate, rotation),
        "actual lower frame geometry leaves the construction cell clear");
    check(
        PlacementGeometry.intersects(Shapes.block(), BlockPos.ZERO, pose, candidate, rotation),
        "a genuinely obstructing solid remains blocked");
    check(
        !PlacementGeometry.intersects(Shapes.empty(), BlockPos.ZERO, pose, candidate, rotation),
        "empty shape does not retain a previous overlap");
    check(
        PlacementGeometry.intersects(Shapes.block(), BlockPos.ZERO, pose, candidate, rotation),
        "reused workspace still detects a solid after an empty query");
    int falseCells = 0;
    for (var v : BogeyVariant.values()) {
      var origin = new Vector3d(0, v.ride, 0);
      pose.position().set(origin);
      pose.rotationPoint().set(.5, .5, .5);
      for (double yaw : new double[] {0, .31, 1.2}) {
        pose.orientation().rotationY(yaw);
        rotation.rotationY(-.21);
        for (int floor : new int[] {v.mountTop, v.compactMountTop}) {
          candidate.set(0, floor + .5, 0);
          for (var cell : BogieCollision.cells(v)) {
            var pos = new BlockPos(cell.x(), cell.y(), cell.z());
            var shape = BogieCollisionShape.bake(cell.boxes());
            check(
                !PlacementGeometry.intersects(shape, pos, pose, candidate, rotation),
                "native frame must not obstruct its platform floor: " + v.id + "/" + v.size);
            if (PlacementGeometry.intersects(Shapes.block(), pos, pose, candidate, rotation))
              falseCells++;
          }
        }
      }
    }
    check(falseCells > 0, "actual catalog reproduces full-cell false obstructions");
    System.out.println(
        "PlatformBuildingCheck: "
            + checks
            + " real SAT checks passed; "
            + falseCells
            + " whole-cell false overlaps cleared without removing solid collisions");
  }
}
