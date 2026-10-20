package com.guidedrailways;

import java.util.*;
import org.joml.Vector3d;

public final class BogeyVariantCheck {
  private static int checks;

  private static void check(boolean result, String why) {
    checks++;
    if (!result) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    var mass =
        com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(
                    java.nio.file.Path.of(
                        "src/main/resources/data/guidedrailways/physics_block_properties/bogie_collision.json")))
            .getAsJsonObject()
            .getAsJsonObject("overrides");
    var ids = new HashSet<String>();
    var catalog =
        com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(
                    java.nio.file.Path.of(
                        "src/main/resources/assets/guidedrailways/geometry/all_bogeys.json")))
            .getAsJsonArray();
    for (var v : BogeyVariant.values()) {
      check(
          ids.add(v.id + "/" + v.size)
              && BogeyVariant.read(v.ordinal()) == v
              && BogeyVariant.find(v.id, v.size) == v,
          "saved indices and style IDs are unambiguous");
      if (v == BogeyVariant.DEFAULT) continue;
      var boxes = BogieCollision.boxes(v);
      var cells = BogieCollision.cells(v);
      var parts = new HashSet<Integer>();
      check(
          cells.stream()
              .anyMatch(
                  c ->
                      c.part() == BogieCollision.CENTRE_PART
                          && c.x() == 0
                          && c.y() == 0
                          && c.z() == 0),
          "native frame retains the central block entity");
      for (var cell : cells) {
        check(
            parts.add(cell.part()) && cell.part() <= 127,
            "every occupied native collision cell has a unique encodable part");
        for (var b : cell.boxes())
          check(
              b.x0() >= 0 && b.y0() >= 0 && b.z0() >= 0 && b.x1() <= 1 && b.y1() <= 1
                  && b.z1() <= 1,
              "physics shape remains inside its owning voxel cell");
      }
      var offset = BogiePairContact.offset(v);
      var half = BogiePairContact.half(v);
      for (var b : boxes)
        for (double x : new double[] {b.x0(), b.x1()})
          for (double y : new double[] {b.y0(), b.y1()})
            for (double z : new double[] {b.z0(), b.z1()}) {
              check(
                  cells.stream()
                      .anyMatch(
                          c ->
                              c.boxes().stream()
                                  .anyMatch(q -> q.contains(x - c.x(), y - c.y(), z - c.z()))),
                  "partitioning never cuts away frame or wheel collision at a cell boundary");
              var point = new Vector3d(x - .5, y - .5, z - .5).sub(offset);
              check(
                  Math.abs(point.x) <= half.x() + 1e-7
                      && Math.abs(point.y) <= half.y() + 1e-7
                      && Math.abs(point.z) <= half.z() + 1e-7,
                  "anti-interlocking pair envelope encloses the selected model's collision");
            }
      double ceiling =
          boxes.stream()
              .filter(
                  b -> {
                    double x = Math.max(0, Math.max(b.x0() - .5, .5 - b.x1())),
                        z = Math.max(0, Math.max(b.z0() - .5, .5 - b.z1()));
                    return x * x + z * z < .5 - 1e-8;
                  })
              .mapToDouble(BogieCollision.Box::y1)
              .max()
              .orElseThrow();
      check(
          v.ride + ceiling - .5 <= v.mountTop - .375 + 1e-6,
          "mount underside clears every animation part under its complete yaw-swept footprint");
      var model =
          com.google.gson.JsonParser.parseString(
                  java.nio.file.Files.readString(
                      java.nio.file.Path.of(
                          "src/main/resources/assets/guidedrailways/models/block/bogie_mount_"
                              + v.ordinal()
                              + ".json")))
              .getAsJsonObject();
      double stem =
          model
                  .getAsJsonArray("elements")
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonArray("from")
                  .get(1)
                  .getAsDouble()
              / 16;
      check(
          Math.abs(stem - v.supportBottom) < 1e-7 && stem >= -1 && stem < .75,
          "per-style rendered support matches collision height and valid model limits");
      if (!v.id.contains("invisible")) {
        double bearing =
            catalog.get(v.ordinal() - 1).getAsJsonObject().get("bearingTop").getAsDouble();
        check(
            Math.abs(v.plateOffset() + stem - bearing + 1.0 / 32) < 1e-6,
            "support overlaps the native central bearing without a floating seam");
      }
      double total = 48;
      for (var cell : cells)
        if (cell.part() != BogieCollision.CENTRE_PART) {
          var props = mass.getAsJsonObject("shape=" + BogieCollision.shapeId(v, cell.part()));
          check(
              props != null,
              "every native outer cell has an explicit mass, from the current shape catalog");
          total += props.get("sable:mass").getAsDouble();
        }
      check(
          Math.abs(total - (cells.size() > 1 ? 80 : 48)) < 1e-6,
          "native body mass remains bounded when the model has more collision cells");
      check(
          v.tread > 0 && v.half >= .5 && Double.isFinite(v.ride),
          "each wheel geometry has finite drive and guidance dimensions");
      RailPlacement.Route straight =
          d ->
              new RailPlacement.Point(
                  new Vector3d(0, 0, d), new Vector3d(0, 0, 1), new Vector3d(0, 1, 0));
      var fit = RailPlacement.bogie(straight, 0, v.ride, v.half, v.pivotHeight());
      check(
          Math.abs(fit.origin().y - v.ride) < 1e-8
              && Math.abs(fit.pivot().y - (v.mountTop - .25)) < 1e-8,
          "each profile places its own wheel height and mounting bearing on the expected grid");
      check(
          Math.abs((fit.front() - fit.rear()) - 2 * v.half) < 1e-5,
          "long native wheelbases receive sufficient front/rear rail length");
      for (int ground : new int[] {-64, -1, 0, 63, 319})
        for (double yaw : new double[] {0, .3, Math.PI / 2, Math.PI, 5.1}) {
          var direction = new Vector3d(Math.sin(yaw), 0, Math.cos(yaw));
          RailPlacement.Route track =
              d ->
                  new RailPlacement.Point(
                      new Vector3d(.5, ground, .5).fma(d, direction),
                      new Vector3d(direction),
                      new Vector3d(0, 1, 0));
          var placed = RailPlacement.bogie(track, 0, v.ride, v.half, v.pivotHeight());
          double floor = placed.origin().y + v.plateOffset() + .5;
          check(
              Math.abs(floor - ground - v.mountTop) < 1e-8
                  && Math.abs(floor - Math.rint(floor)) < 1e-8,
              "every native platform top aligns to an integer height above the rail-supporting ground, at any yaw and negative elevation");
          check(
              Math.abs(placed.pivot().y + .25 - floor) < 1e-8,
              "rerail bearing and rendered platform top use the same integral floor");
        }
    }
    check(
        BogeyVariant.read(-1) == BogeyVariant.DEFAULT
            && BogeyVariant.read(999) == BogeyVariant.DEFAULT,
        "unknown persisted indices fall back safely");
    check(
        BogeyVariant.find("railways:large_create_style_0_12_0", "create:large") != null,
        "largest six-axle steam style has its own large profile");
    check(
        BogeyVariant.values().length == 40,
        "all 39 native style/size combinations plus standalone core geometry are registered");
    var small = BogeyVariant.defaultFor(RailGauge.STANDARD);
    var large = BogeyVariant.find("create:standard", "create:large");
    RailPlacement.Route straight =
        d ->
            new RailPlacement.Point(
                new Vector3d(0, 0, d), new Vector3d(0, 0, 1), new Vector3d(0, 1, 0));
    var mixed =
        RailPlacement.carriage(
            straight,
            List.of(new Vector3d(0, 0, -4), new Vector3d(0, large.mountTop - small.mountTop, 4)),
            0,
            new Vector3d(0, 0, 1),
            List.of(
                new RailPlacement.Dimensions(small.ride, small.half, small.pivotHeight()),
                new RailPlacement.Dimensions(large.ride, large.half, large.pivotHeight())));
    check(
        mixed.bogies().size() == 2 && mixed.rotation().isFinite(),
        "a shared carriage can fit different native mounting heights without assigning one profile to both bogeys");
    System.out.println("BogeyVariantCheck: " + checks + " assertions passed");
  }
}
