package com.guidedrailways;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class RailDockClient {
  static void register(IEventBus bus) {
    bus.addListener(
        (EntityRenderersEvent.RegisterRenderers event) ->
            event.registerBlockEntityRenderer(
                RailDockContent.ENTITY.get(), DockingConnectorRenderer::new));
  }
}
