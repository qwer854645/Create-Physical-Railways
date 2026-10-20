package com.guidedrailways;

import com.google.gson.*;
import com.guidedrailways.NativeCouplerAnimation.Part;
import com.mojang.blaze3d.vertex.*;
import com.railwayteam.railways.content.buffer.IDyedBuffer;
import dev.ryanhcode.sable.companion.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Native JSON elements are baked once per resource reload and painted variant, then animated by
 * mechanical part.
 */
final class NativeCouplerRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
  private final NativeCouplerAnimation.Workspace animation = new NativeCouplerAnimation.Workspace();
  private static final Map<String, Mesh> MESHES = new HashMap<>();

  private record Element(BlockElement element, Part part) {}

  private static final class Mesh {
    final BlockModel model;
    final List<Element> elements;
    final Map<String, Map<Part, List<BakedQuad>>> baked = new HashMap<>();
    final double shift;

    Mesh(BlockModel model, List<Element> elements, double shift) {
      this.model = model;
      this.elements = elements;
      this.shift = shift;
    }

    Map<Part, List<BakedQuad>> quads(String paint) {
      return baked.computeIfAbsent(
          paint,
          key -> {
            var out = new EnumMap<Part, List<BakedQuad>>(Part.class);
            var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            var bakery = new FaceBakery();
            for (var element : elements)
              for (var face : element.element.faces.entrySet()) {
                var texture = model.getMaterial(face.getValue().texture()).texture();
                if (texture.getPath().equals("block/buffer/small_buffer") && !paint.isEmpty())
                  texture =
                      ResourceLocation.fromNamespaceAndPath(
                          "railways", "block/buffer/small_buffer/small_buffer_" + paint);
                var quad =
                    bakery.bakeQuad(
                        element.element.from,
                        element.element.to,
                        face.getValue(),
                        atlas.apply(texture),
                        face.getKey(),
                        BlockModelRotation.X0_Y0,
                        element.element.rotation,
                        element.element.shade);
                out.computeIfAbsent(element.part, p -> new ArrayList<>()).add(quad);
              }
            return out;
          });
    }
  }

  static void register(IEventBus bus) {
    bus.addListener(
        (RegisterClientReloadListenersEvent e) ->
            e.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    NativeCouplerRenderer::reload));
    bus.addListener(
        (ModelEvent.ModifyBakingResult e) ->
            e.getModels()
                .replaceAll(
                    (id, model) ->
                        id.id().getNamespace().equals("railways") ? new Filter(model) : model));
    bus.addListener(
        (EntityRenderersEvent.RegisterRenderers e) -> {
          e.registerBlockEntityRenderer(
              com.railwayteam.railways.registry.CRBlockEntities.DYEABLE.get(),
              c -> new NativeCouplerRenderer<>());
          e.registerBlockEntityRenderer(
              com.railwayteam.railways.registry.CRBlockEntities.HEADSTOCK.get(),
              c -> new NativeCouplerRenderer<>());
          e.registerBlockEntityRenderer(
              com.railwayteam.railways.registry.CRBlockEntities.COPYCAT_HEADSTOCK.get(),
              c -> new NativeCouplerRenderer<>());
        });
  }

  private static String modelName(CouplerKind kind) {
    return switch (kind) {
      case LINK -> "link_and_pin";
      case LINKLESS -> "link_and_pin_linkless";
      case KNUCKLE -> "knuckle_coupler";
      case KNUCKLE_SPLIT -> "split_knuckle_coupler";
      case SCREWLINK -> "screwlink_coupler";
      default -> throw new IllegalArgumentException();
    };
  }

  private static String path(String mount, CouplerKind kind, boolean flip) {
    return mount.equals("single_deco")
        ? "single_deco/" + modelName(kind)
        : "headstock/" + mount + "_" + modelName(kind) + (flip ? "_upside_down" : "");
  }

  private static void reload(ResourceManager resources) {
    HardwareRenderData.ready = false;
    MESHES.clear();
    try {
      for (var kind : CouplerKind.values())
        if (kind != CouplerKind.STANDARD)
          for (String mount : List.of("single_deco", "wooden_headstock", "copycat_headstock"))
            for (boolean flip : new boolean[] {false, true}) {
              if (flip && mount.equals("single_deco")) continue;
              String path = path(mount, kind, flip);
              try (var reader =
                  resources.openAsReader(
                      ResourceLocation.fromNamespaceAndPath(
                          "railways", "models/block/buffer/" + path + ".json"))) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var model = BlockModel.fromString(json.toString());
                var names = new HashMap<Integer, String>();
                NativeCouplerAnimation.groups(json.getAsJsonArray("groups"), names);
                var elements = new ArrayList<Element>();
                for (int i = 0; i < model.getElements().size(); i++) {
                  var element = model.getElements().get(i);
                  boolean attachment =
                      element.faces.values().stream()
                          .anyMatch(
                              f ->
                                  model
                                      .getMaterial(f.texture())
                                      .texture()
                                      .getNamespace()
                                      .equals("railways"));
                  if (!attachment) continue;
                  String group = names.getOrDefault(i, "");
                  Part part =
                      NativeCouplerAnimation.classify(
                          kind,
                          group,
                          new Vector3d(element.from),
                          new Vector3d(element.to),
                          mount.equals("single_deco"));
                  elements.add(new Element(element, part));
                }
                if (elements.isEmpty())
                  throw new IllegalStateException("No native coupler elements: " + path);
                MESHES.put(
                    path, new Mesh(model, elements, mount.equals("single_deco") ? 0 : -3 / 16.0));
              }
            }
      HardwareRenderData.ready = true;
    } catch (Exception failure) {
      MESHES.clear();
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .error("[CouplerHardware] Native model reload failed; keeping static models", failure);
    }
  }

  private static String mount(BlockState state) {
    String id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    return id.contains("copycat")
        ? "copycat_headstock"
        : id.contains("headstock") ? "wooden_headstock" : "single_deco";
  }

  private static boolean flip(BlockState state) {
    for (var p : state.getProperties())
      if (p.getName().equals("upside_down")) return Boolean.TRUE.equals(state.getValue(p));
    return false;
  }

  private static final class Filter extends BakedModelWrapper<BakedModel> {
    Filter(BakedModel original) {
      super(original);
    }

    @Override
    public ModelData getModelData(
        net.minecraft.world.level.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos,
        BlockState state,
        ModelData data) {
      var original = super.getModelData(level, pos, state, data);
      var levelNow = Minecraft.getInstance().level;
      boolean dynamic =
          levelNow != null
              && HardwareRenderData.ready
              && RailwayHardware.coupler(state)
              && SableCompanion.INSTANCE.getContaining(levelNow, pos) != null;
      return original.derive().with(HardwareRenderData.DYNAMIC, dynamic).build();
    }

    @Override
    public List<BakedQuad> getQuads(
        BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
      var quads = super.getQuads(state, side, random, data, type);
      if (!Boolean.TRUE.equals(data.get(HardwareRenderData.DYNAMIC))) return quads;
      return quads.stream()
          .filter(
              q ->
                  !q.getSprite().contents().name().getNamespace().equals("railways")
                      || !q.getSprite().contents().name().getPath().startsWith("block/buffer/"))
          .toList();
    }
  }

  private static ClientSubLevelAccess body(CouplerEndpoint e) {
    var b = SableCompanion.INSTANCE.getContaining(e.getLevel(), e.getBlockPos());
    return b instanceof ClientSubLevelAccess c ? c : null;
  }

  private static void part(
      PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads, int light, int overlay) {
    if (quads != null)
      for (var quad : quads) consumer.putBulkData(pose.last(), quad, 1, 1, 1, 1, light, overlay);
  }

  private static Vector3d canonical(
      CouplerEndpoint e,
      dev.ryanhcode.sable.companion.math.Pose3dc ownPose,
      Quaterniond facing,
      Vector3d world) {
    return ownPose
        .transformPositionInverse(world, new Vector3d())
        .sub(e.getBlockPos().getX() + .5, e.getBlockPos().getY() + .5, e.getBlockPos().getZ() + .5)
        .rotate(new Quaterniond(facing).conjugate())
        .add(.5, .5, .5);
  }

  @Override
  public void render(
      T owner, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    var e = CouplerEndpoint.get(owner);
    if (e == null || !HardwareRenderData.ready) return;
    var own = body(e);
    if (own == null) return;
    var ownPose = own.renderPose(partial);
    var kind = RailwayHardware.kind(e);
    if (kind == null || kind == CouplerKind.STANDARD) return;
    String mounting = mount(e.getBlockState());
    var mesh = MESHES.get(path(mounting, kind, flip(e.getBlockState())));
    if (mesh == null) return;
    String paint =
        owner instanceof IDyedBuffer dyed && dyed.getColor() != null
            ? dyed.getColor().getName()
            : "";
    var quads = mesh.quads(paint);
    var facing = e.getBlockState().getValue(CouplerBlock.FACING);
    var facingRotation =
        new Quaterniond()
            .rotationTo(
                new Vector3d(0, 0, -1), new Vector3d(facing.getStepX(), 0, facing.getStepZ()));
    double p = e.hardwareProgress(partial);
    var peer =
        e.visualPeerPos != null && e.getLevel().hasChunkAt(e.visualPeerPos)
            ? CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos))
            : null;
    var other = peer == null ? null : body(peer);
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
      var origin =
          new Vector3d(
              peer.getBlockPos().getX(), peer.getBlockPos().getY(), peer.getBlockPos().getZ());
      var otherPose = other.renderPose(partial);
      var target =
          otherPose.transformPosition(new Vector3d(peer.anchor).add(origin), new Vector3d());
      toward = canonical(e, ownPose, facingRotation, target);
      var base = otherPose.transformPosition(origin, new Vector3d());
      var top = otherPose.transformPosition(new Vector3d(origin).add(0, 1, 0), new Vector3d());
      peerUp =
          canonical(e, ownPose, facingRotation, top)
              .sub(canonical(e, ownPose, facingRotation, base));
    }
    var transforms =
        NativeCouplerAnimation.transforms(
            kind,
            p,
            valid ? peer.hardwareProgress(partial) : 0,
            mesh.shift,
            valid,
            active,
            toward,
            peerUp,
            animation);
    if (quads.get(Part.LINK) == null && active && p > 0) {
      quads = new EnumMap<>(quads);
      quads.put(
          Part.LINK,
          MESHES
              .get(path(mounting, CouplerKind.LINK, flip(e.getBlockState())))
              .quads(paint)
              .get(Part.LINK));
    }
    pose.pushPose();
    pose.translate(.5, .5, .5);
    pose.mulPose(new Quaternionf(facingRotation));
    pose.translate(-.5, -.5, -.5);
    var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
    for (var entry : quads.entrySet()) {
      pose.pushPose();
      pose.mulPose(new org.joml.Matrix4f(transforms.get(entry.getKey())));
      part(pose, consumer, entry.getValue(), light, overlay);
      pose.popPose();
    }
    pose.popPose();
  }

  @Override
  public boolean shouldRenderOffScreen(T owner) {
    return false;
  }

  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox(T owner) {
    var endpoint = CouplerEndpoint.get(owner);
    return endpoint == null
        ? new net.minecraft.world.phys.AABB(owner.getBlockPos())
        : CouplerRenderBounds.get(endpoint);
  }
}
