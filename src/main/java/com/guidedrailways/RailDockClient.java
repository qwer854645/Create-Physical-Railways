package com.guidedrailways;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The docking connector renders through Simulated's renderer, so the [BerPerf] lines never included
 * it: this mod registers that renderer, but the work belongs to the dependency, and a dock is only on
 * screen at a station. That is exactly the shape of the remaining hitch, so the renderer is wrapped to
 * time it like the others. Behaviour is unchanged; the delegate draws everything.
 */
final class RailDockClient {
  static void register(IEventBus bus) {
    bus.addListener(
        (EntityRenderersEvent.RegisterRenderers event) ->
            event.registerBlockEntityRenderer(
                RailDockContent.ENTITY.get(),
                (BlockEntityRendererProvider<DockingConnectorBlockEntity>) Timed::new));
  }

  private static final class Timed implements BlockEntityRenderer<DockingConnectorBlockEntity> {
    private final DockingConnectorRenderer delegate;

    Timed(BlockEntityRendererProvider.Context context) {
      delegate = new DockingConnectorRenderer(context);
    }

    @Override
    public void render(
        DockingConnectorBlockEntity dock,
        float partial,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay) {
      long start = BerPerf.start();
      try {
        delegate.render(dock, partial, pose, buffers, light, overlay);
      } finally {
        BerPerf.record("dock", start);
      }
    }

    @Override
    public boolean shouldRender(DockingConnectorBlockEntity dock, Vec3 camera) {
      return delegate.shouldRender(dock, camera);
    }

    @Override
    public AABB getRenderBoundingBox(DockingConnectorBlockEntity dock) {
      return delegate.getRenderBoundingBox(dock);
    }
  }
}
