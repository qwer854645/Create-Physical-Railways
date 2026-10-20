package com.guidedrailways;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bake an encountered cell once. Unused styles do not construct voxel shapes at startup. */
final class BogieCollisionShape {
  private static final ConcurrentMap<Integer, VoxelShape> CACHE = new ConcurrentHashMap<>();

  private BogieCollisionShape() {}

  static VoxelShape shape(int id) {
    return CACHE.computeIfAbsent(id, key -> bake(BogieCollision.shape(key).cell().boxes()));
  }

  static VoxelShape centre(BogeyVariant variant) {
    return CACHE.computeIfAbsent(
        -1 - variant.ordinal(),
        key ->
            bake(
                BogieCollision.cells(variant).stream()
                    .filter(cell -> cell.part() == BogieCollision.CENTRE_PART)
                    .findFirst()
                    .orElseThrow()
                    .boxes()));
  }

  static VoxelShape bake(List<BogieCollision.Box> boxes) {
    var result = Shapes.empty();
    for (var box : boxes) {
      result =
          Shapes.joinUnoptimized(
              result,
              Shapes.box(box.x0(), box.y0(), box.z0(), box.x1(), box.y1(), box.z1()),
              BooleanOp.OR);
    }
    return result.optimize();
  }
}
