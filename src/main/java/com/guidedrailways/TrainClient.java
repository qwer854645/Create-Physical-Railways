package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.StandardBogeyRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class TrainClient {
  static void register(IEventBus bus) {
    net.createmod.ponder.foundation.PonderIndex.addPlugin(new RailwayPonderPlugin());
    ItemDescriptions.register(bus);
    NativeBogeyCapture.register();
    NativeTrainClient.register();
    if (net.neoforged.fml.ModList.get().isLoaded("railways")) {
      NativeCouplerRenderer.register(bus);
      SteamRailwaysClient.register();
      bus.addListener(
          (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) ->
              e.enqueueWork(SteamRailwaysBogeys::verifyRegisteredStyles));
    }
    if (net.neoforged.fml.ModList.get().isLoaded("create_bb")) BlocksBogiesClient.register();
    DriverClient.register();
    bus.addListener(
        (net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) ->
            e.register(Content.DRIVER_MENU.get(), DriverScreen::new));
    bus.addListener(
        (EntityRenderersEvent.RegisterRenderers e) -> {
          e.registerBlockEntityRenderer(Content.BOGIE_ENTITY.get(), Renderer::new);
          e.registerBlockEntityRenderer(Content.COUPLER_ENTITY.get(), CouplerRenderer::new);
          e.registerBlockEntityRenderer(Content.DRIVER_ENTITY.get(), DriverConsoleRenderer::new);
        });
  }

  private static final class Renderer implements BlockEntityRenderer<BogieEntity> {
    private final StandardBogeyRenderer.Small renderer = new StandardBogeyRenderer.Small();
    private final CompoundTag data = new CompoundTag();

    Renderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(
        BogieEntity entity,
        float partial,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay) {
      double distance = entity.animationDistance(partial);
      pose.pushPose();
      pose.translate(.5, BogieBlock.variant(entity.getBlockState()).renderY, .5);
      if (BogieBlock.variant(entity.getBlockState()) != BogeyVariant.DEFAULT
          && NativeBogeyModels.render(
              BogieBlock.variant(entity.getBlockState()),
              entity.bogeyData,
              (float)
                  (distance
                      / BogieBlock.variant(entity.getBlockState()).tread
                      / BogieBlock.variant(entity.getBlockState()).animationRatio
                      * 180
                      / Math.PI),
              partial,
              pose,
              buffers,
              light,
              overlay)) {
        pose.popPose();
        return;
      }
      renderer.render(
          data,
          (float) (distance / BogieGeometry.TREAD_RADIUS * 180 / Math.PI),
          partial,
          pose,
          buffers,
          light,
          overlay,
          false);
      pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BogieEntity entity) {
      return false;
    }

    @Override
    public int getViewDistance() {
      return Math.max(
          64,
          net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BogieEntity entity) {
      // NeoForge's renderer default ignores CachedRenderBBBlockEntity and supplies one block.
      // A one-tick travel margin covers the logical/render pose difference used by Sable's frustum
      // hook.
      return entity.getRenderBoundingBox().inflate(.125 + Math.abs(entity.speed) / 20);
    }

    @Override
    public boolean shouldRender(BogieEntity entity, net.minecraft.world.phys.Vec3 camera) {
      var box = getRenderBoundingBox(entity);
      double range = getViewDistance();
      return RenderVisibility.within(
          box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, camera.x, camera.y, camera.z,
          range);
    }
  }
}
