package com.guidedrailways;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Verifies the exact runtime catalog, including mass selectors and collision-cell identity. */
public final class CollisionCatalogCheck {
  public static void main(String[] args) throws Exception {
    var file =
        Path.of(
            "src/main/resources/data/guidedrailways/physics_block_properties/bogie_collision.json");
    var expected = new JsonObject();
    expected.addProperty("selector", "guidedrailways:bogie_collision");
    var properties = new JsonObject();
    properties.addProperty("sable:mass", 0);
    properties.addProperty("sable:friction", .2);
    properties.addProperty("sable:restitution", 0);
    expected.add("properties", properties);
    var overrides = new JsonObject();
    expected.add("overrides", overrides);
    var ids = new HashSet<Integer>();
    for (var variant : BogeyVariant.values()) {
      double mass = 48;
      for (var cell : BogieCollision.cells(variant)) {
        if (cell.part() == BogieCollision.CENTRE_PART) continue;
        int id = BogieCollision.shapeId(variant, cell.part());
        var shape = BogieCollision.shape(id);
        if (!ids.add(id)
            || shape.id() != id
            || shape.variant() != variant
            || !shape.cell().equals(cell)) throw new AssertionError("catalog identity mismatch");
        var entry = new JsonObject();
        entry.addProperty("sable:mass", shape.mass());
        overrides.add("shape=" + id, entry);
        mass += shape.mass();
      }
      double target = BogieCollision.cells(variant).size() > 1 ? 80 : 48;
      if (Math.abs(mass - target) > 1e-7)
        throw new AssertionError("mass changed: " + variant + " " + mass);
    }
    if (ids.size() != BogieCollision.shapeCount()
        || !ids.contains(0)
        || !ids.contains(ids.size() - 1)) throw new AssertionError("catalog is not contiguous");
    if (args.length == 1 && args[0].equals("--write"))
      Files.writeString(
          file, new GsonBuilder().setPrettyPrinting().create().toJson(expected) + "\n");
    if (!JsonParser.parseString(Files.readString(file)).equals(expected))
      throw new AssertionError(
          "collision mass metadata differs from runtime catalog; run generateCollisionMetadata");
    var blockstate =
        JsonParser.parseString(
                Files.readString(
                    Path.of(
                        "src/main/resources/assets/guidedrailways/blockstates/bogie_collision.json")))
            .getAsJsonObject()
            .getAsJsonObject("variants");
    if (blockstate.size() != 1 || !blockstate.has(""))
      throw new AssertionError("collision model must cover every shape state");
    System.out.println(
        "CollisionCatalogCheck: "
            + ids.size()
            + " shapes; all model masses and cell identities verified (previous blockstate count 20480)");
  }
}
