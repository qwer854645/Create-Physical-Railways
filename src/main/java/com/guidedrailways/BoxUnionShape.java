package com.guidedrailways;

import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Build the exact box union in one coordinate grid instead of repeatedly merging grids. */
final class BoxUnionShape extends ArrayVoxelShape {
  private BoxUnionShape(DiscreteVoxelShape grid, double[] x, double[] y, double[] z) {
    super(grid, x, y, z);
  }

  static VoxelShape of(List<AABB> boxes) {
    if (boxes.isEmpty()) return Shapes.empty();
    if (boxes.size() == 1) {
      var b = boxes.getFirst();
      return Shapes.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    double[] x = coords(boxes, 0), y = coords(boxes, 1), z = coords(boxes, 2);
    var grid = new BitSetDiscreteVoxelShape(x.length - 1, y.length - 1, z.length - 1);
    for (var b : boxes) {
      int x0 = index(x, b.minX),
          x1 = index(x, b.maxX),
          y0 = index(y, b.minY),
          y1 = index(y, b.maxY),
          z0 = index(z, b.minZ),
          z1 = index(z, b.maxZ);
      for (int i = x0; i < x1; i++)
        for (int j = y0; j < y1; j++) for (int k = z0; k < z1; k++) grid.fill(i, j, k);
    }
    return new BoxUnionShape(grid, x, y, z);
  }

  private static double[] coords(List<AABB> boxes, int axis) {
    var values = new double[boxes.size() * 2];
    int n = 0;
    for (var b : boxes) {
      values[n++] = axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ;
      values[n++] = axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ;
    }
    Arrays.sort(values);
    int size = 0;
    for (double v : values) if (size == 0 || v - values[size - 1] > 1e-7) values[size++] = v;
    return Arrays.copyOf(values, size);
  }

  private static int index(double[] coords, double value) {
    int i = Arrays.binarySearch(coords, value);
    if (i >= 0) return i;
    int insertion = -i - 1;
    return insertion > 0 && value - coords[insertion - 1] <= 1e-7 ? insertion - 1 : insertion;
  }
}
