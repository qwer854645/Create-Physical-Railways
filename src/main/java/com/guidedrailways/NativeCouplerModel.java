package com.guidedrailways;

import com.google.gson.*;
import com.guidedrailways.NativeCouplerAnimation.Part;
import java.io.IOException;
import java.util.*;
import org.joml.Vector3d;

/** One element classification and geometry description for both native rendering and collision. */
final class NativeCouplerModel {
  record Piece(int index, Part part, List<Vector3d> corners, boolean physics, boolean attachment) {}

  interface Source {
    JsonObject read(String namespace, String path) throws IOException;
  }

  static double shift(boolean headstock) {
    return headstock ? -3 / 16.0 : 0;
  }

  static CouplerKind kind(String modelPath) {
    return modelPath.contains("split_knuckle")
        ? CouplerKind.KNUCKLE_SPLIT
        : modelPath.contains("knuckle_coupler")
            ? CouplerKind.KNUCKLE
            : modelPath.contains("screwlink_coupler")
                ? CouplerKind.SCREWLINK
                : modelPath.contains("link_and_pin_linkless")
                    ? CouplerKind.LINKLESS
                    : modelPath.contains("link_and_pin") ? CouplerKind.LINK : null;
  }

  static String path(String mount, CouplerKind kind, boolean flip) {
    String name =
        switch (kind) {
          case LINK -> "link_and_pin";
          case LINKLESS -> "link_and_pin_linkless";
          case KNUCKLE -> "knuckle_coupler";
          case KNUCKLE_SPLIT -> "split_knuckle_coupler";
          case SCREWLINK -> "screwlink_coupler";
          default -> throw new IllegalArgumentException("No native model for " + kind);
        };
    return mount.equals("single_deco")
        ? "single_deco/" + name
        : "headstock/" + mount + "_" + name + (flip ? "_upside_down" : "");
  }

  /** The caller selects the resource source; client packs must not change server collision. */
  static JsonObject resolve(JsonObject model, Source source) throws IOException {
    var chain = new ArrayList<JsonObject>();
    var parents = new HashSet<String>();
    chain.add(model);
    while (!model.has("elements")) {
      if (!model.has("parent")) throw new IOException("Missing coupler model elements");
      String id = model.get("parent").getAsString();
      if (!parents.add(id) || parents.size() > 32)
        throw new IOException("Cyclic coupler model parent " + id);
      int separator = id.indexOf(':');
      model =
          source.read(
              separator < 0 ? "minecraft" : id.substring(0, separator),
              "models/" + (separator < 0 ? id : id.substring(separator + 1)));
      chain.add(model);
    }
    var resolved = model.deepCopy();
    for (int i = chain.size() - 2; i >= 0; i--)
      for (var entry : chain.get(i).entrySet()) {
        if (entry.getKey().equals("textures")) {
          var textures =
              resolved.has("textures") ? resolved.getAsJsonObject("textures") : new JsonObject();
          entry
              .getValue()
              .getAsJsonObject()
              .entrySet()
              .forEach(e -> textures.add(e.getKey(), e.getValue().deepCopy()));
          resolved.add("textures", textures);
        } else resolved.add(entry.getKey(), entry.getValue().deepCopy());
      }
    return resolved;
  }

  static List<Piece> pieces(JsonObject model, CouplerKind kind, boolean headstock) {
    var names = new HashMap<Integer, String>();
    NativeCouplerAnimation.groups(model.getAsJsonArray("groups"), names);
    boolean automatic = kind == CouplerKind.KNUCKLE || kind == CouplerKind.KNUCKLE_SPLIT;
    var pieces = new ArrayList<Piece>();
    int index = 0;
    for (var value : model.getAsJsonArray("elements")) {
      var element = value.getAsJsonObject();
      var from = vector(element.getAsJsonArray("from"));
      var to = vector(element.getAsJsonArray("to"));
      var group = names.getOrDefault(index++, "");
      var part =
          kind == null
              ? Part.MOUNT
              : NativeCouplerAnimation.classify(kind, group, from, to, !headstock);
      // Wooden/copycat beams stay fixed even when the attached coupler animates.
      boolean attachment = false;
      for (var face : element.getAsJsonObject("faces").entrySet()) {
        String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
        int hops = 0;
        while (texture.startsWith("#")
            && model.has("textures")
            && model.getAsJsonObject("textures").has(texture.substring(1))
            && hops++ < 16)
          texture = model.getAsJsonObject("textures").get(texture.substring(1)).getAsString();
        if (texture.startsWith("railways:")) attachment = true;
      }
      if (!attachment) part = Part.MOUNT;
      boolean physical =
          !Set.of("link", "pin", "flat_links", "shackle", "screw").contains(group)
              && !(automatic && from.z < (headstock ? 9 : 12) && !(headstock && from.z >= 13));
      var corners = new ArrayList<Vector3d>();
      for (int i = 0; i < 8; i++) {
        var point =
            new Vector3d(
                (i & 1) == 0 ? from.x : to.x,
                (i & 2) == 0 ? from.y : to.y,
                (i & 4) == 0 ? from.z : to.z);
        if (element.has("rotation")) {
          var rotation = element.getAsJsonObject("rotation");
          var origin = vector(rotation.getAsJsonArray("origin"));
          double angle = Math.toRadians(rotation.get("angle").getAsDouble());
          point.sub(origin);
          switch (rotation.get("axis").getAsString()) {
            case "x" -> point.rotateX(angle);
            case "y" -> point.rotateY(angle);
            case "z" -> point.rotateZ(angle);
          }
          if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
            double scale = 1 / Math.cos(angle);
            switch (rotation.get("axis").getAsString()) {
              case "x" -> point.mul(1, scale, scale);
              case "y" -> point.mul(scale, 1, scale);
              case "z" -> point.mul(scale, scale, 1);
            }
          }
          point.add(origin);
        }
        corners.add(point.div(16));
      }
      pieces.add(new Piece(index - 1, part, List.copyOf(corners), physical, attachment));
    }
    return List.copyOf(pieces);
  }

  private static Vector3d vector(JsonArray a) {
    return new Vector3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
  }

  private NativeCouplerModel() {}
}
