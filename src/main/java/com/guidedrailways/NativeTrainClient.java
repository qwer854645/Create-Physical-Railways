package com.guidedrailways;

import com.simibubi.create.CreateClient;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client registry mirrors use the same native Train subtype as the server. No carriage entities.
 */
final class NativeTrainClient {
  static void register() {
    NativeTrainNetwork.client =
        tag -> {
          if (Minecraft.getInstance().level == null || !tag.hasUUID("Id")) return;
          var manager = CreateClient.RAILWAYS;
          var id = tag.getUUID("Id");
          var previous = manager.trains.get(id);
          if (tag.getBoolean("Removed")) {
            if (previous instanceof PhysicalTrainProxy proxy) {
              var station = proxy.getCurrentStation();
              proxy.station(null);
              PhysicalTrainProxy.syncStation(proxy, station);
              manager.trains.remove(id);
            }
            return;
          }
          if (previous != null && !(previous instanceof PhysicalTrainProxy)) {
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .error("Native train UUID collision on client: {}", id);
            return;
          }
          var proxy = previous instanceof PhysicalTrainProxy p ? p : new PhysicalTrainProxy(id);
          var oldStation = proxy.getCurrentStation();
          proxy.apply(tag, manager.trackNetworks);
          PhysicalTrainProxy.syncStation(proxy, oldStation);
          manager.trains.put(id, proxy);
        };
    NeoForge.EVENT_BUS.addListener(
        (RegisterClientCommandsEvent event) ->
            event
                .getDispatcher()
                .register(
                    Commands.literal("physicalrailways_client")
                        .then(
                            Commands.literal("trains")
                                .executes(
                                    context -> {
                                      var player = Minecraft.getInstance().player;
                                      if (player == null) return 0;
                                      var trains =
                                          CreateClient.RAILWAYS.trains.values().stream()
                                              .filter(PhysicalTrainProxy.class::isInstance)
                                              .toList();
                                      player.displayClientMessage(
                                          Component.translatable(
                                              "message.guidedrailways.identity.count",
                                              trains.size()),
                                          false);
                                      for (var train : trains)
                                        player.displayClientMessage(
                                            NativeTrainDiagnostics.describe(train), false);
                                      return trains.size();
                                    }))));
  }

  private NativeTrainClient() {}
}
