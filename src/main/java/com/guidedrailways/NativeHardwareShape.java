package com.guidedrailways;

import com.google.gson.*;
import com.guidedrailways.NativeCouplerAnimation.Part;
import dev.ryanhcode.sable.companion.SableCompanion;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Installed model elements are parsed once; living collision shares the renderer's part transforms.
 */
public final class NativeHardwareShape {
  private record Key(BlockState state, boolean physics) {}

  private record Mesh(List<NativeCouplerModel.Piece> pieces, Matrix4d facing, double shift) {}

  private static final ThreadLocal<NativeCouplerAnimation.Workspace> ANIMATION =
      ThreadLocal.withInitial(NativeCouplerAnimation.Workspace::new);
  private static final Map<Key, VoxelShape> CACHE = new ConcurrentHashMap<>();
  private static final Map<BlockState, Mesh> MESHES = new ConcurrentHashMap<>();

  public static VoxelShape get(BlockState state, boolean physics, VoxelShape fallback) {
    return CACHE.computeIfAbsent(
        new Key(state, physics),
        key -> {
          try {
            return bake(mesh(state), physics, null);
          } catch (Exception failure) {
            warn(state, failure);
            return fallback;
          }
        });
  }

  private static final Set<BlockState> WARNED = ConcurrentHashMap.newKeySet();

  private static void warn(BlockState state, Exception error) {
    if (!WARNED.add(state)) return;
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .warn("[CouplerHardware] Shape fallback for {}", state, error);
  }

  public static VoxelShape living(CouplerEndpoint e, VoxelShape fallback) {
    var own = SableCompanion.INSTANCE.getContaining(e.getLevel(), e.getBlockPos());
    if (own == null) return get(e.getBlockState(), false, fallback);
    long tick = e.getLevel().getGameTime();
    if (e.livingShape != null && e.livingShapeTick == tick) return e.livingShape;
    try {
      var state = e.getBlockState();
      var mesh = mesh(state);
      var kind = RailwayHardware.kind(e);
      if (kind == null) return get(state, false, fallback);
      var peer =
          e.visualPeerPos != null && e.getLevel().hasChunkAt(e.visualPeerPos)
              ? CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos))
              : null;
      var other =
          peer == null
              ? null
              : SableCompanion.INSTANCE.getContaining(peer.getLevel(), peer.getBlockPos());
      boolean valid =
          e.visualConnected
              && e.hasGeometry
              && peer != null
              && peer.visualConnected
              && peer.hasGeometry
              && peer.endpointId.equals(e.visualPeerId)
              && e.endpointId.equals(peer.visualPeerId)
              && other != null;
      boolean active = valid && e.endpointId.compareTo(peer.endpointId) < 0;
      Vector3d toward = null;
      var peerUp = new Vector3d(0, 1, 0);
      if (valid) {
        var point =
            new Vector3d(peer.anchor)
                .add(
                    peer.getBlockPos().getX(),
                    peer.getBlockPos().getY(),
                    peer.getBlockPos().getZ());
        var target = other.logicalPose().transformPosition(point, new Vector3d());
        toward =
            own.logicalPose()
                .transformPositionInverse(target, new Vector3d())
                .sub(e.getBlockPos().getX(), e.getBlockPos().getY(), e.getBlockPos().getZ());
        new Matrix4d(mesh.facing()).invert().transformPosition(toward);
        var up = other.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
        own.logicalPose().transformNormalInverse(up, peerUp);
        new Matrix4d(mesh.facing()).invert().transformDirection(peerUp);
        if (!toward.isFinite() || toward.distanceSquared(new Vector3d(.5, .5, .5)) > 16) {
          valid = false;
          active = false;
          toward = null;
          peerUp.set(0, 1, 0);
        }
      }
      double p = e.hardwareProgress(0);
      var transforms =
          NativeCouplerAnimation.transforms(
              kind,
              p,
              valid ? peer.hardwareProgress(0) : 0,
              mesh.shift(),
              valid,
              active,
              toward,
              peerUp,
              ANIMATION.get());
      // A borrowed link is included in the same grid rather than merged into a second shape.
      var collisionMesh =
          kind == CouplerKind.LINKLESS && active && p > 0 ? linkedMesh(state) : mesh;
      var shape = bake(collisionMesh, false, transforms);
      e.livingShape = shape;
      e.livingShapeTick = tick;
      return shape;
    } catch (Exception failure) {
      warn(e.getBlockState(), failure);
      return fallback;
    }
  }

  private static Mesh mesh(BlockState state) {
    return MESHES.computeIfAbsent(
        state,
        key -> {
          try {
            return load(state, null);
          } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
          }
        });
  }

  private static final Map<BlockState, Mesh> LINKED_MESHES = new ConcurrentHashMap<>();

  private static Mesh linkedMesh(BlockState state) {
    return LINKED_MESHES.computeIfAbsent(
        state,
        key -> {
          var base = mesh(state);
          var pieces = new ArrayList<>(base.pieces());
          for (var piece : linkMesh(state).pieces())
            if (piece.part() == Part.LINK) pieces.add(piece);
          return new Mesh(List.copyOf(pieces), base.facing(), base.shift());
        });
  }

  private static final Map<BlockState, Mesh> LINK_MESHES = new ConcurrentHashMap<>();

  private static Mesh linkMesh(BlockState state) {
    return LINK_MESHES.computeIfAbsent(
        state,
        key -> {
          try {
            return load(state, "link_and_pin");
          } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
          }
        });
  }

  private static VoxelShape bake(Mesh mesh, boolean physics, Map<Part, Matrix4d> transforms) {
    var boxes = new ArrayList<net.minecraft.world.phys.AABB>(mesh.pieces().size());
    var matrix = new Matrix4d();
    var min = new Vector3d();
    var max = new Vector3d();
    var point = new Vector3d();
    for (var piece : mesh.pieces()) {
      if (physics && !piece.physics()) continue;
      matrix.set(mesh.facing());
      if (transforms != null) matrix.mul(transforms.get(piece.part()));
      min.set(Double.POSITIVE_INFINITY);
      max.set(Double.NEGATIVE_INFINITY);
      for (var corner : piece.corners()) {
        var p = matrix.transformPosition(corner, point);
        min.min(p);
        max.max(p);
      }
      if (max.x - min.x > 1e-6 && max.y - min.y > 1e-6 && max.z - min.z > 1e-6)
        boxes.add(new net.minecraft.world.phys.AABB(min.x, min.y, min.z, max.x, max.y, max.z));
    }
    return BoxUnionShape.of(boxes);
  }

  private static JsonObject json(Class<?> owner, String namespace, String path) throws IOException {
    try (var stream = owner.getResourceAsStream("/assets/" + namespace + "/" + path + ".json")) {
      if (stream == null) throw new FileNotFoundException(path);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static <T extends Comparable<T>> String serialized(
      BlockState state, net.minecraft.world.level.block.state.properties.Property<T> p) {
    return p.getName(state.getValue(p));
  }

  private static boolean matches(BlockState state, String variant) {
    if (variant.isEmpty()) return true;
    for (String entry : variant.split(",")) {
      var pair = entry.split("=");
      if (pair.length != 2) return false;
      boolean found = false;
      for (var p : state.getProperties())
        if (p.getName().equals(pair[0])) found = serialized(state, p).equals(pair[1]);
      if (!found) return false;
    }
    return true;
  }

  private static Mesh load(BlockState state, String substitute) throws IOException {
    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    var owner = state.getBlock().getClass();
    var variants =
        json(owner, id.getNamespace(), "blockstates/" + id.getPath()).getAsJsonObject("variants");
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
    var modelId = variant.get("model").getAsString().split(":");
    if (substitute != null) modelId[1] = modelId[1].replace("link_and_pin_linkless", substitute);
    var model = json(owner, modelId[0], "models/" + modelId[1]);
    model = NativeCouplerModel.resolve(model, (namespace, path) -> json(owner, namespace, path));
    double yaw = variant.has("y") ? Math.toRadians(-variant.get("y").getAsDouble()) : 0,
        pitch = variant.has("x") ? Math.toRadians(-variant.get("x").getAsDouble()) : 0;
    var facing =
        new Matrix4d().translation(.5, .5, .5).rotateY(yaw).rotateX(pitch).translate(-.5, -.5, -.5);
    boolean headstock = id.getPath().contains("headstock");
    var pieces =
        NativeCouplerModel.pieces(
            model,
            substitute != null ? CouplerKind.LINK : NativeCouplerModel.kind(modelId[1]),
            headstock);
    return new Mesh(List.copyOf(pieces), facing, NativeCouplerModel.shift(headstock));
  }
}
