package com.guidedrailways;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared cached collision/outline geometry, also usable by offline shape checks. */
final class MountShapes {
  private static final Map<BogeyVariant, VoxelShape> STANDARD = new HashMap<>();
  private static final Map<BogeyVariant, VoxelShape> COMPACT = new HashMap<>();

  static {
    for (var v : BogeyVariant.values()) {
      var stem = Shapes.box(5 / 16d, v.supportBottom, 5 / 16d, 11 / 16d, .75, 11 / 16d);
      STANDARD.put(v, Shapes.or(stem, Shapes.box(0, .75, 0, 1, 1, 1)));
      var shape =
          Shapes.box(
              5 / 16d,
              v.supportBottom + v.mountTop - v.compactMountTop,
              5 / 16d,
              11 / 16d,
              .75,
              11 / 16d);
      for (var b : v.compactPlate)
        shape = Shapes.or(shape, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
      COMPACT.put(v, shape);
    }
  }

  static VoxelShape get(BogeyVariant variant, boolean compact) {
    return (compact ? COMPACT : STANDARD).get(variant);
  }

  private MountShapes() {}
}
