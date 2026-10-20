package com.guidedrailways;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public final class BogieCollisionCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static List<double[]> mesh(ZipFile zip, String name) throws Exception {
    var vertices = new ArrayList<double[]>();
    var text =
        new String(
            zip.getInputStream(
                    zip.getEntry("assets/create/models/block/track/bogey/" + name + ".obj"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    for (var line : text.split("\n"))
      if (line.startsWith("v ")) {
        var a = line.trim().split("\s+");
        vertices.add(
            new double[] {
              Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])
            });
      }
    return vertices;
  }

  public static void main(String[] args) throws Exception {
    try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
      for (var v : mesh(zip, "bogey_frame"))
        check(
            BogieCollision.containsAfterCellClipping(
                .5 + v[0] * .998046875,
                BogieGeometry.RENDER_Y + v[1] * .998046875,
                .5 + v[2] * .998046875),
            "frame vertex outside collision " + java.util.Arrays.toString(v));
      for (int phase = 0; phase < 64; phase++) {
        double a = phase * Math.PI / 32, c = Math.cos(a), s = Math.sin(a);
        for (double z : new double[] {-1, 1})
          for (var v : mesh(zip, "bogey_wheel")) {
            double y = c * v[1] - s * v[2], dz = s * v[1] + c * v[2];
            check(
                BogieCollision.containsAfterCellClipping(
                    .5 + v[0], BogieGeometry.RENDER_Y + .75 + y, .5 + z + dz),
                "rotated wheel or axle outside collision "
                    + java.util.Arrays.toString(v)
                    + " phase="
                    + phase);
          }
      }
    }
    for (var cell : BogieCollision.CELLS)
      for (var b : cell.boxes())
        check(
            b.x0() >= 0 && b.y0() >= 0 && b.z0() >= 0 && b.x1() <= 1 && b.y1() <= 1 && b.z1() <= 1,
            "native baker must not truncate a collision cell");
    check(
        BogieCollision.CELLS.stream().anyMatch(c -> c.part() == BogieCollision.CENTRE_PART),
        "central actor cell must be populated");
    double low =
        BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::y0).min().orElseThrow();
    check(
        BogieGeometry.RIDE_HEIGHT - .5 + low > .125,
        "expanded collision must clear Create's 2/16-high track collision");
    double minZ =
        BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::z0).min().orElseThrow();
    double maxZ =
        BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::z1).max().orElseThrow();
    check(
        Math.abs((maxZ - minZ) - 2.989619) < 1e-6,
        "anti-interlock belt adds only 1/128 clearance at each end");
    // Reject the old square wheel corners while retaining the full swept rim.
    double cy = BogieGeometry.RENDER_Y + .75;
    for (double side : new double[] {-1, 1})
      for (double end : new double[] {-1, 1}) {
        double x = .5 + side * .8, z = .5 + end;
        check(
            !BogieCollision.containsAfterCellClipping(x, cy - .4, z + end * .4),
            "empty diagonal wheel corner must not collide");
        for (int angle = 0; angle < 360; angle++) {
          double a = Math.toRadians(angle);
          check(
              BogieCollision.containsAfterCellClipping(
                  x,
                  cy + BogieCollision.RIM_RADIUS * Math.sin(a),
                  z + BogieCollision.RIM_RADIUS * Math.cos(a)),
              "swept flange circumference must remain covered");
        }
        check(
            !BogieCollision.containsAfterCellClipping(.5 + side * 1.0, cy - .45, z),
            "tread must not inherit flange radius");
      }
    check(
        !BogieCollision.containsAfterCellClipping(
            .5 + .6 * .998046875,
            BogieGeometry.RENDER_Y + .41 * .998046875,
            .5 + 1.32 * .998046875),
        "empty frame diamond corner must not collide");
    check(
        BogieCollision.BOXES.size() <= 304,
        "keep collision primitives bounded without adding bodies");
    // No slots remain along the horizontal contact perimeter, including between axles.
    for (int i = 0; i <= 200; i++) {
      double f = i / 200.0, x = 1.203125, z = 1 + BogieCollision.RIM_RADIUS + 1.0 / 128;
      for (double side : new double[] {-1, 1}) {
        check(
            BogieCollision.containsAfterCellClipping(.5 + side * x, cy, .5 - z + 2 * z * f),
            "side belt must cover every flange gap");
        check(
            BogieCollision.containsAfterCellClipping(.5 - x + 2 * x * f, cy, .5 + side * z),
            "end belt must prevent head-on wheel interlock");
      }
    }
    var resource = Path.of("src/main/resources/data/guidedrailways/physics_block_properties");
    var core =
        com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(resource.resolve("bogie_frame.json")))
            .getAsJsonObject();
    var parts =
        com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(resource.resolve("bogie_collision.json")))
            .getAsJsonObject();
    double total = 0, cx = 0, cyMass = 0, cz = 0, ix = 0, iy = 0, iz = 0;
    for (var cell : BogieCollision.CELLS) {
      double mass;
      if (cell.part() == BogieCollision.CENTRE_PART)
        mass = core.getAsJsonObject("properties").get("sable:mass").getAsDouble();
      else {
        var overrides = parts.getAsJsonObject("overrides");
        var override =
            overrides.getAsJsonObject(
                "shape=" + BogieCollision.shapeId(BogeyVariant.DEFAULT, cell.part()));
        mass =
            override == null
                ? parts.getAsJsonObject("properties").get("sable:mass").getAsDouble()
                : override.get("sable:mass").getAsDouble();
      }
      check(mass >= 0, "mass may not be negative");
      total += mass;
      cx += mass * cell.x();
      cyMass += mass * cell.y();
      cz += mass * cell.z();
      ix += mass * (1.0 / 6 + cell.y() * cell.y() + cell.z() * cell.z());
      iy += mass * (1.0 / 6 + cell.x() * cell.x() + cell.z() * cell.z());
      iz += mass * (1.0 / 6 + cell.x() * cell.x() + cell.y() * cell.y());
    }
    check(Math.abs(total - 80) < 1e-9, "distributed layout preserves 80 total mass");
    check(
        Math.abs(cx) + Math.abs(cyMass) + Math.abs(cz) < 1e-9,
        "distributed layout preserves the original centre of mass");
    check(
        ix > 3 * (80.0 / 6) && iy > 5 * (80.0 / 6) && iz > 3 * (80.0 / 6),
        "distributed point-mass layout increases inertia; native shape inertia is verified at runtime");
    check(
        Math.abs(BogieCollision.massCenterY() - .49853515625) < 1e-9,
        "explicit mass centre preserves original central collision bounds");
    check(
        core.getAsJsonObject("properties").get("sable:restitution").getAsDouble() == 0
            && parts.getAsJsonObject("properties").get("sable:restitution").getAsDouble() == 0,
        "bogie contacts remain non-bouncy");
    System.out.println(
        "BogieCollisionCheck: "
            + checks
            + " assertions passed; primitives="
            + BogieCollision.BOXES.size());
  }
}
