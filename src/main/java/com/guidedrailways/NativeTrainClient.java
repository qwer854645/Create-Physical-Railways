package com.guidedrailways;

import com.simibubi.create.CreateClient;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client registry mirrors use the same native Train subtype as the server. No carriage entities.
 */
final class NativeTrainClient {
  private record PendingSignal(String dimension, SignalFrame frame, long tick) {}

  private static final Map<UUID, PendingSignal> PENDING = new HashMap<>();
  private static long tick;

  private static void signal(CompoundTag tag) {
    try {
      var id = tag.getUUID("Id");
      var pending =
          new PendingSignal(
              tag.getString("Dimension"), SignalFrame.read(tag.getCompound("Signals")), tick);
      var previous = PENDING.get(id);
      if (previous != null
          && previous.dimension.equals(pending.dimension)
          && previous.frame.revision() > pending.frame.revision()) return;
      if (PENDING.size() >= 1024 && !PENDING.containsKey(id))
        PENDING.remove(PENDING.keySet().iterator().next());
      PENDING.put(id, pending);
      if (Minecraft.getInstance().level != null) applyPending(id);
    } catch (IllegalArgumentException error) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn("Invalid native signal snapshot", error);
    }
  }

  private static void applyPending(UUID id) {
    var pending = PENDING.get(id);
    var manager = CreateClient.RAILWAYS;
    if (pending == null
        || !(manager.trains.get(id) instanceof PhysicalTrainProxy proxy)
        || !proxy.getPresentDimensions().getFirst().location().toString().equals(pending.dimension))
      return;
    proxy.signals.accept(pending.frame, manager.signalEdgeGroups, manager.trains);
    PENDING.remove(id);
  }

  private static void clear() {
    PENDING.clear();
    tick = 0;
    var trains = CreateClient.RAILWAYS.trains;
    for (var id : List.copyOf(trains.keySet()))
      if (trains.get(id) instanceof PhysicalTrainProxy proxy) {
        release(proxy);
        trains.remove(id);
      }
  }

  private static void release(PhysicalTrainProxy proxy) {
    proxy.clearJourney();
    var station = proxy.getCurrentStation();
    proxy.station(null);
    PhysicalTrainProxy.syncStation(proxy, station);
  }

  static void register() {
    NativeTrainNetwork.client =
        tag -> {
          if (!tag.hasUUID("Id")) return;
          if (tag.getBoolean("SignalOnly")) {
            signal(tag);
            return;
          }
          var manager = CreateClient.RAILWAYS;
          var id = tag.getUUID("Id");
          var previous = manager.trains.get(id);
          if (tag.getBoolean("Removed")) {
            PENDING.remove(id);
            if (previous instanceof PhysicalTrainProxy proxy) {
              release(proxy);
              manager.trains.remove(id);
            }
            return;
          }
          if (Minecraft.getInstance().level == null) return;
          if (previous != null && !(previous instanceof PhysicalTrainProxy)) {
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .error("Native train UUID collision on client: {}", id);
            return;
          }
          var proxy = previous instanceof PhysicalTrainProxy p ? p : new PhysicalTrainProxy(id);
          if (!proxy
              .getPresentDimensions()
              .getFirst()
              .location()
              .toString()
              .equals(tag.getString("Dimension"))) proxy.signals.resetClient();
          var oldStation = proxy.getCurrentStation();
          proxy.apply(tag, manager.trackNetworks, Minecraft.getInstance().level.registryAccess());
          PhysicalTrainProxy.syncStation(proxy, oldStation);
          manager.trains.put(id, proxy);
          applyPending(id);
        };
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
          if (Minecraft.getInstance().level == null) return;
          tick++;
          PENDING.values().removeIf(p -> tick - p.tick > 200);
          var manager = CreateClient.RAILWAYS;
          for (var id : List.copyOf(PENDING.keySet())) applyPending(id);
          for (var train : manager.trains.values())
            if (train instanceof PhysicalTrainProxy proxy) {
              if (tick % 10 == 0)
                proxy.refreshClientGraph(
                    manager.trackNetworks, Minecraft.getInstance().level.registryAccess());
              proxy.signals.retry(manager.signalEdgeGroups, manager.trains);
            }
        });
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> clear());
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
