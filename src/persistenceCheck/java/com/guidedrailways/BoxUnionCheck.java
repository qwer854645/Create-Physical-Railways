package com.guidedrailways;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

public final class BoxUnionCheck {
  public static void main(String[] args) {
    var random = new Random(140230L);
    int checks = 0;
    for (int scene = 0; scene < 80; scene++) {
      var boxes = new ArrayList<AABB>();
      var expected = Shapes.empty();
      for (int i = 0; i < 2 + scene % 12; i++) {
        double x = random.nextDouble() * 2 - 1,
            y = random.nextDouble() * 2 - 1,
            z = random.nextDouble() * 2 - 1;
        var b =
            new AABB(
                x,
                y,
                z,
                x + .02 + random.nextDouble(),
                y + .02 + random.nextDouble(),
                z + .02 + random.nextDouble());
        boxes.add(b);
        expected =
            Shapes.joinUnoptimized(
                expected, Shapes.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ), BooleanOp.OR);
      }
      var actual = BoxUnionShape.of(boxes);
      if (Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME))
        throw new AssertionError("box union changed");
      checks++;
      for (var axis : Direction.Axis.values())
        for (int i = 0; i < 12; i++) {
          var moving =
              new AABB(-.15, -.15, -.15, .15, .15, .15)
                  .move(
                      random.nextDouble() * 4 - 2,
                      random.nextDouble() * 4 - 2,
                      random.nextDouble() * 4 - 2);
          double distance = random.nextDouble() * 4 - 2;
          if (Math.abs(
                  expected.collide(axis, moving, distance) - actual.collide(axis, moving, distance))
              > 1e-7) throw new AssertionError("player collision changed");
          checks++;
        }
    }
    var close = List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(1 - 1e-8, .2, .3, 2, .7, .9));
    var expected = Shapes.or(Shapes.box(0, 0, 0, 1, 1, 1), Shapes.box(1 - 1e-8, .2, .3, 2, .7, .9));
    if (Shapes.joinIsNotEmpty(expected, BoxUnionShape.of(close), BooleanOp.NOT_SAME))
      throw new AssertionError("vanilla epsilon merge differs");
    if (!BoxUnionShape.of(List.of()).isEmpty()) throw new AssertionError("empty union");
    System.out.println(
        "BoxUnionCheck: " + checks + " exact union and axis-collision comparisons passed");
  }
}
