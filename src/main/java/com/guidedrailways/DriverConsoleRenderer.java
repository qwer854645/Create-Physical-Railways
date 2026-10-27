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

  /** Lamp recesses in the screen texture, mapped into the plate: u = 4, 8, 12 and v = 11, two texels. */
  private static final double LAMP_SIZE = 2 * 5 / 16d;

  /**
   * Draws the three indicator lamps over the recesses painted in the screen texture.
   *
   * <p>The lamps used to be a block state variant, which made every change cost a client chunk rebuild
   * and forced a throttle; a quickly moved lever then outran them. Drawn here, they follow the lever on
   * the very next frame and cost three quads.
   */
  private static void drawLamps(
      DriverConsoleEntity e,
      PoseStack pose,
      MultiBufferSource buffers,
      java.util.function.Function<ResourceLocation, net.minecraft.client.renderer.texture.TextureAtlasSprite> atlas,
      int overlay) {
    int mask = ConsoleDisplay.lampMask(e.bound, e.emergency, e.notch);
    if (mask == 0) return;
    var buffer =
        buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
    var white =
        atlas.apply(ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete"));
    int[] slots = {ConsoleDisplay.LEFT, ConsoleDisplay.MIDDLE, ConsoleDisplay.RIGHT};
    for (int i = 0; i < slots.length; i++) {
      if ((mask & slots[i]) == 0) continue;
      double x = 3 + (4 + i * 4) * 5 / 16d;
      double z = 4 + 11 * 5 / 16d;
      lamp(pose.last(), buffer, white, x, z, x + LAMP_SIZE, z + LAMP_SIZE,
          ConsoleDisplay.colour(slots[i], e.emergency), overlay);
    }
  }

  /** One flat, self-lit quad: full brightness so the lamp reads in an unlit shed. */
  private static void lamp(
      PoseStack.Pose pose,
      VertexConsumer buffer,
      net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
      double x0,
      double z0,
      double x1,
      double z1,
      int rgb,
      int overlay) {
    float u = (sprite.getU0() + sprite.getU1()) / 2f;
    float v = (sprite.getV0() + sprite.getV1()) / 2f;
    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
    float y = 11.42f / 16f;
    double[][] corners = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
    for (var corner : corners)
      buffer
          .addVertex(pose, (float) corner[0], y, (float) corner[1])
          .setColor(r, g, b, 255)
          .setUv(u, v)
          .setOverlay(overlay)
          .setLight(0xF000F0)
          .setNormal(pose, 0, 1, 0);
  }
  @Override
  public boolean shouldRenderOffScreen(DriverConsoleEntity e) {
    return true;
  }
}
