package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** One moving throttle/brake handle. Neither handle adds collision, joints or rigid bodies. */
public final class DriverConsoleRenderer implements BlockEntityRenderer<DriverConsoleEntity> {
  DriverConsoleRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(
      DriverConsoleEntity e,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    long berStart = BerPerf.start();
    try {
      draw(e, partial, pose, buffers, light, overlay);
    } finally {
      BerPerf.record("console", berStart);
    }
  }

  private void draw(
      DriverConsoleEntity e,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
    var iron =
        atlas.apply(ResourceLocation.fromNamespaceAndPath("create", "block/industrial_iron_block"));
    var dark =
        atlas.apply(ResourceLocation.fromNamespaceAndPath("create", "block/dark_metal_block"));
    var vertices = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
    pose.pushPose();
    pose.translate(.5, 0, .5);
    pose.mulPose(
        Axis.YP.rotationDegrees(
            -((e.getBlockState().getValue(DriverConsoleBlock.FACING).toYRot() + 180) % 360)));
    pose.translate(-.5, 0, -.5);
    pose.translate(.5, .625, .5);
    pose.mulPose(Axis.XP.rotationDegrees(22.5f));
    pose.translate(-.5, -.625, -.5);
    drawLamps(e, pose, buffers, atlas, overlay);
    pose.pushPose();
    pose.translate(11 / 16d, 12 / 16d, 7.5 / 16d);
    pose.mulPose(
        Axis.XP.rotationDegrees(
            -36 * net.minecraft.util.Mth.lerp(partial, e.previousLever, e.lever)));
    pose.translate(-11 / 16d, -12 / 16d, -7.5 / 16d);
    CouplerRenderer.box(
        pose.last(),
        vertices,
        iron,
        10.5 / 16,
        12 / 16d,
        7 / 16d,
        11.5 / 16,
        14 / 16d,
        8 / 16d,
        light,
        overlay);
    CouplerRenderer.box(
        pose.last(),
        vertices,
        dark,
        9.5 / 16,
        14 / 16d,
        6.5 / 16,
        12.5 / 16,
        15 / 16d,
        8.5 / 16,
        light,
        overlay);
    pose.popPose();
    pose.popPose();
  }

  /** One line per second: whether the renderer even believes a lamp is lit, and why. */
  private static void diagnose(DriverConsoleEntity e, int mask) {
    long now = System.currentTimeMillis();
    if (now < LAMP_LOG_AT) return;
    LAMP_LOG_AT = now + 1000;
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[ConsoleLamp] bound={} notch={} emergency={} mask={}",
            e.bound,
            e.notch,
            e.emergency,
            mask);
  }

  private static long LAMP_LOG_AT;
  /** Lamp recesses in the screen texture, mapped into the plate: u = 4, 8, 12 and v = 11, two texels. */
  private static final double LAMP_SIZE = 2 * 5 / 16d;

  /**
   * Draws the three indicator lamps over the recesses painted in the screen texture.
   *
   * <p>Thin boxes through the same helper the lever uses: a hand-emitted quad did not appear at all,
   * and this path is already proven to render. The lamps used to be block state variants, which made
   * every change cost a client chunk rebuild and forced a throttle the lever then outran.
   */
  private static void drawLamps(
      DriverConsoleEntity e,
      PoseStack pose,
      MultiBufferSource buffers,
      java.util.function.Function<ResourceLocation, net.minecraft.client.renderer.texture.TextureAtlasSprite> atlas,
      int overlay) {
    int mask = ConsoleDisplay.lampMask(e.bound, e.emergency, e.notch);
    if (TrainConfig.DETAILED_LOGGING.get()) diagnose(e, mask);
    if (mask == 0) return;
    var vertices = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
    int[] slots = {ConsoleDisplay.LEFT, ConsoleDisplay.MIDDLE, ConsoleDisplay.RIGHT};
    for (int i = 0; i < slots.length; i++) {
      if ((mask & slots[i]) == 0) continue;
      var sprite =
          atlas.apply(
              ResourceLocation.fromNamespaceAndPath(
                  "minecraft", ConsoleDisplay.blockTexture(slots[i], e.emergency)));
      double x = 3 + (4 + i * 4) * 5 / 16d;
      double z = 4 + 11 * 5 / 16d;
      CouplerRenderer.box(
          pose.last(),
          vertices,
          sprite,
          x,
          11.42 / 16d,
          z,
          x + LAMP_SIZE,
          11.50 / 16d,
          z + LAMP_SIZE,
          0xF000F0,
          overlay);
    }
  }
  @Override
  public boolean shouldRenderOffScreen(DriverConsoleEntity e) {
    return true;
  }
}
