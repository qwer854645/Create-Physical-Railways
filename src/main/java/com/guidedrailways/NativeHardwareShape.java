package com.guidedrailways;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.joml.Vector3d;

/**
 * Common-side fixed casting geometry from the installed Beta assets. Never loads client model
 * classes.
 */
public final class NativeHardwareShape {
  private record Key(BlockState state, boolean physics) {}

  private static final Map<Key, VoxelShape> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

  public static VoxelShape get(BlockState state, boolean physics, VoxelShape fallback) {
    return CACHE.computeIfAbsent(
        new Key(state, physics),
        key -> {
          try {
            return load(state, physics);
          } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .warn("[CouplerHardware] Shape fallback for {}", state, failure);
            return fallback;
          }
        });
  }

  private static JsonObject json(Class<?> resourceOwner, String namespace, String path)
      throws IOException {
    try (var stream =
        resourceOwner.getResourceAsStream("/assets/" + namespace + "/" + path + ".json")) {
      if (stream == null) throw new FileNotFoundException(path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static boolean matches(BlockState state, String variant) {
    for (String entry : variant.split(",")) {
      String[] pair = entry.split("=");
      if (pair.length != 2) return false;
      boolean found = false;
      for (var property : state.getProperties())
        if (property.getName().equals(pair[0])) found = serialized(state, property).equals(pair[1]);
      if (!found) return false;
    }
    return true;
  }

  private static <T extends Comparable<T>> String serialized(
      BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
    return property.getName(state.getValue(property));
  }

  private static VoxelShape load(BlockState state, boolean physics) throws IOException {
    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    var resourceOwner = state.getBlock().getClass();
    var variants =
        json(resourceOwner, id.getNamespace(), "blockstates/" + id.getPath())
            .getAsJsonObject("variants");
    JsonObject variant = null;
    for (var entry : variants.entrySet())
      if (matches(state, entry.getKey())) {
        variant =
            entry.getValue().isJsonArray()
                ? entry.getValue().getAsJsonArray().get(0).getAsJsonObject()
                : entry.getValue().getAsJsonObject();
        break;
      }
    if (variant == null) throw new IOException("Unmatched hardware variant " + state);
    String[] modelId = variant.get("model").getAsString().split(":");
    var model = json(resourceOwner, modelId[0], "models/" + modelId[1]);
    while (!model.has("elements")) {
      var parent = model.get("parent").getAsString().split(":");
      model = json(resourceOwner, parent[0], "models/" + parent[1]);
    }
    double yaw = variant.has("y") ? Math.toRadians(-variant.get("y").getAsDouble()) : 0;
    double pitch = variant.has("x") ? Math.toRadians(-variant.get("x").getAsDouble()) : 0;
    boolean automatic = serializedKind(state).startsWith("knuckle");
    boolean headstock = id.getPath().contains("headstock");
    var shapes = new ArrayList<VoxelShape>();
    var moving = new HashSet<Integer>();
    if (physics && model.has("groups"))
      for (var value : model.getAsJsonArray("groups"))
        if (value.isJsonObject()) {
          var group = value.getAsJsonObject();
          String name = group.get("name").getAsString();
          if (Set.of("link", "pin", "flat_links", "shackle", "screw").contains(name))
            for (var child : group.getAsJsonArray("children"))
              if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber())
                moving.add(child.getAsInt());
        }
    int index = -1;
    for (var value : model.getAsJsonArray("elements")) {
      if (moving.contains(++index)) continue;
      var element = value.getAsJsonObject();
      var from = vector(element.getAsJsonArray("from"));
      var to = vector(element.getAsJsonArray("to"));
      // The draft joint carries the movable automatic head. A coarse head box would prevent the
      // jaws mating.
      // Keep the sleeve and the entire beam; player collision still includes every original
      // element.
      if (physics && automatic && from.z < (headstock ? 9 : 12) && !(headstock && from.z >= 13))
        continue;
      var min = new Vector3d(Double.POSITIVE_INFINITY);
      var max = new Vector3d(Double.NEGATIVE_INFINITY);
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
        point.sub(8, 8, 8).rotateX(pitch).rotateY(yaw).add(8, 8, 8).div(16);
        min.min(point);
        max.max(point);
      }
      if (max.x - min.x > 1e-6 && max.y - min.y > 1e-6 && max.z - min.z > 1e-6)
        shapes.add(Shapes.box(min.x, min.y, min.z, max.x, max.y, max.z));
    }
    return shapes.isEmpty()
        ? Shapes.empty()
        : Shapes.or(shapes.removeFirst(), shapes.toArray(VoxelShape[]::new)).optimize();
  }

  private static String serializedKind(BlockState state) {
    for (var p : state.getProperties())
      if (p.getName().equals("style")) return serialized(state, p);
    return "";
  }

  private static Vector3d vector(JsonArray a) {
    return new Vector3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
  }
}
