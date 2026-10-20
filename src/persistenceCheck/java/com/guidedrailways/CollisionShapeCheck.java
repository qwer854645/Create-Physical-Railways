package com.guidedrailways;

import net.minecraft.world.phys.shapes.*;

/** Actual Minecraft shape unions, no world/server. */
public final class CollisionShapeCheck {
  public static void main(String[] args) {
    int checked = 0;
    for (var variant :
        new BogeyVariant[] {
          BogeyVariant.DEFAULT, BogeyVariant.read(1), BogeyVariant.read(20), BogeyVariant.read(39)
        }) {
      var central = BogieCollisionShape.centre(variant);
      if (central != BogieCollisionShape.centre(variant))
        throw new AssertionError("central shape not cached");
      var cells = BogieCollision.cells(variant);
      for (int i = 0; i < cells.size(); i += Math.max(1, cells.size() / 3)) {
        var cell = cells.get(i);
        var expected = Shapes.empty();
        for (var b : cell.boxes())
          expected =
              Shapes.or(expected, Shapes.box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()));
        var actual =
            cell.part() == BogieCollision.CENTRE_PART
                ? central
                : BogieCollisionShape.shape(BogieCollision.shapeId(variant, cell.part()));
        if (Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME))
          throw new AssertionError("collision union changed: " + variant + "/" + cell.part());
        if (cell.part() != BogieCollision.CENTRE_PART
            && actual != BogieCollisionShape.shape(BogieCollision.shapeId(variant, cell.part())))
          throw new AssertionError("outer shape not cached");
        checked++;
      }
    }
    System.out.println(
        "CollisionShapeCheck: "
            + checked
            + " actual Minecraft voxel unions and cache identities passed");
  }
}
