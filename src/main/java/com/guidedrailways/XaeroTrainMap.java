package com.guidedrailways;

import com.simibubi.create.CreateClient;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.*;

/** Optional native Xaero overlay elements, backed by existing physical train identities. */
final class XaeroTrainMap {
  record Marker(String name, Vec3 previous, Vec3 position, boolean derailed) {
    double coordinate(int axis, float partial) {
      double from = axis == 0 ? previous.x : axis == 1 ? previous.y : previous.z;
      double to = axis == 0 ? position.x : axis == 1 ? position.y : position.z;
      return from + (to - from) * Math.clamp(partial, 0, 1);
    }
  }

  private static final class Context {
    List<Marker> markers = List.of();
    Iterator<Marker> iterator = Collections.emptyIterator();
  }

  private static final MinimapElementRenderLocation LOCATION =
      MinimapElementRenderLocation.OVER_MINIMAP;
  private static final Context CONTEXT = new Context();
  private static final Renderer RENDERER = new Renderer();
  private static Map<UUID, Marker> previous = Map.of();
  private static MinimapElementRendererHandler handler;
  private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
  private static boolean disabled;

  static void register() {
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> clear());
  }

  private static void clear() {
    previous = Map.of();
    CONTEXT.markers = List.of();
    CONTEXT.iterator = Collections.emptyIterator();
    observedLevel = null;
  }

  private static void tick() {
    if (disabled) return;
    try {
      var mc = Minecraft.getInstance();
      if (mc.level == null || observedLevel != mc.level) {
        clear();
        observedLevel = mc.level;
      }
      if (mc.level == null) return;
      var hud = HudMod.INSTANCE;
      if (hud == null || hud.getMinimap() == null) return;
      var nextHandler = hud.getMinimap().getOverMapRendererHandler();
      if (nextHandler == null) return;
      if (handler != nextHandler) {
        nextHandler.add(RENDERER);
        handler = nextHandler;
      }
      var markers = new LinkedHashMap<UUID, Marker>();
      for (var train : CreateClient.RAILWAYS.trains.values()) {
        if (!(train instanceof PhysicalTrainProxy proxy)) continue;
        var position = proxy.mapPosition(mc.level);
        if (position == null) continue;
        var old = previous.get(proxy.id);
        var from =
            old == null || old.position.distanceToSqr(position) > 64 ? position : old.position;
        markers.put(proxy.id, new Marker(proxy.name.getString(), from, position, proxy.derailed));
      }
      previous = markers;
      CONTEXT.markers = List.copyOf(markers.values());
    } catch (LinkageError error) {
      disabled = true;
      clear();
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("Xaero train markers disabled: unsupported optional minimap API", error);
    }
  }

  private static final class Provider extends MinimapElementRenderProvider<Marker, Context> {
    @Override
    public void begin(MinimapElementRenderLocation location, Context context) {
      context.iterator = context.markers.iterator();
    }

    @Override
    public boolean hasNext(MinimapElementRenderLocation location, Context context) {
      return context.iterator.hasNext();
    }

    @Override
    public Marker getNext(MinimapElementRenderLocation location, Context context) {
      return context.iterator.next();
    }

    @Override
    public void end(MinimapElementRenderLocation location, Context context) {
      context.iterator = Collections.emptyIterator();
    }
  }

  private static final class Reader extends MinimapElementReader<Marker, Context> {
    @Override
    public boolean isHidden(Marker marker, Context context) {
      return disabled;
    }

    @Override
    public double getRenderX(Marker m, Context c, float p) {
      return m.coordinate(0, p);
    }

    @Override
    public double getRenderY(Marker m, Context c, float p) {
      return m.coordinate(1, p);
    }

    @Override
    public double getRenderZ(Marker m, Context c, float p) {
      return m.coordinate(2, p);
    }

    @Override
    public int getInteractionBoxLeft(Marker m, Context c, float p) {
      return -4;
    }

    @Override
    public int getInteractionBoxRight(Marker m, Context c, float p) {
      return 4;
    }

    @Override
    public int getInteractionBoxTop(Marker m, Context c, float p) {
      return -5;
    }

    @Override
    public int getInteractionBoxBottom(Marker m, Context c, float p) {
      return 5;
    }

    @Override
    public int getRenderBoxLeft(Marker m, Context c, float p) {
      return -44;
    }

    @Override
    public int getRenderBoxRight(Marker m, Context c, float p) {
      return 44;
    }

    @Override
    public int getRenderBoxTop(Marker m, Context c, float p) {
      return -14;
    }

    @Override
    public int getRenderBoxBottom(Marker m, Context c, float p) {
      return 5;
    }

    @Override
    public int getLeftSideLength(Marker m, Minecraft mc) {
      return 4;
    }

    @Override
    public String getMenuName(Marker m) {
      return m.name;
    }

    @Override
    public String getFilterName(Marker m) {
      return m.name;
    }

    @Override
    public int getMenuTextFillLeftPadding(Marker m) {
      return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(Marker m) {
      return 0xff303030;
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
      return false;
    }

    @Override
    public boolean isInteractable(MinimapElementRenderLocation location, Marker m) {
      return false;
    }
  }

  private static final class Renderer extends MinimapElementRenderer<Marker, Context> {
    Renderer() {
      super(new Reader(), new Provider(), CONTEXT);
    }

    @Override
    public boolean shouldRender(MinimapElementRenderLocation location) {
      return !disabled && location == LOCATION;
    }

    @Override
    public void preRender(
        MinimapElementRenderInfo info,
        MultiBufferSource.BufferSource buffers,
        MultiTextureRenderTypeRendererProvider textures) {}

    @Override
    public void postRender(
        MinimapElementRenderInfo info,
        MultiBufferSource.BufferSource buffers,
        MultiTextureRenderTypeRendererProvider textures) {}

    @Override
    public boolean renderElement(
        Marker marker,
        boolean highlighted,
        boolean edge,
        double scale,
        float optionalScale,
        double dx,
        double dz,
        MinimapElementRenderInfo info,
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers) {
      if (edge || disabled) return false;
      var pose = graphics.pose();
      var matrix = pose.last().pose();
      int color = marker.derailed ? 0xffd56a60 : CreateGuiTheme.ACCENT;
      quad(buffers, matrix, -4, -5, 4, 4, 0xff202329);
      quad(buffers, matrix, -3, -4, 3, 2, color);
      quad(buffers, matrix, -2, -3, 2, -1, 0xff42494f);
      quad(buffers, matrix, -3, 3, -1, 5, 0xffc6c6c6);
      quad(buffers, matrix, 1, 3, 3, 5, 0xffc6c6c6);
      var font = Minecraft.getInstance().font;
      String name = CreateGuiTheme.fit(font, marker.name, 110);
      pose.pushPose();
      pose.scale(.65f, .65f, 1);
      font.drawInBatch(
          name,
          -font.width(name) / 2f,
          -19,
          0xfff0f0f0,
          true,
          pose.last().pose(),
          buffers,
          Font.DisplayMode.NORMAL,
          0,
          LightTexture.FULL_BRIGHT);
      pose.popPose();
      return true;
    }

    private static void quad(
        MultiBufferSource.BufferSource buffers,
        Matrix4f matrix,
        float x0,
        float y0,
        float x1,
        float y1,
        int color) {
      var vertices = buffers.getBuffer(RenderType.gui());
      vertices.addVertex(matrix, x0, y1, 0).setColor(color);
      vertices.addVertex(matrix, x1, y1, 0).setColor(color);
      vertices.addVertex(matrix, x1, y0, 0).setColor(color);
      vertices.addVertex(matrix, x0, y0, 0).setColor(color);
    }
  }

  private XaeroTrainMap() {}
}
