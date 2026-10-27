package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
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

  @Override
  public boolean shouldRenderOffScreen(DriverConsoleEntity e) {
    return true;
  }
}
