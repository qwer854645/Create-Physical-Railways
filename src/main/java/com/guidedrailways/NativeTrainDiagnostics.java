package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class NativeTrainDiagnostics {
  static Component describe(Train train) {
    var pos =
        train.getPresentDimensions().stream()
            .findFirst()
            .flatMap(train::getPositionInDimension)
            .orElse(net.minecraft.core.BlockPos.ZERO);
    return Component.translatable(
        "message.guidedrailways.identity.entry",
        train.name,
        train.id.toString(),
        train.carriages.size(),
        pos.getX(),
        pos.getY(),
        pos.getZ(),
        Math.round(Math.abs(train.speed) * 200) / 10.0,
        Component.translatable(
            train.derailed
                ? "message.guidedrailways.identity.off_track"
                : "message.guidedrailways.identity.on_track"));
  }

  static void register(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("physicalrailways")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.literal("trains")
                        .executes(
                            context -> {
                              var trains =
                                  Create.RAILWAYS.trains.values().stream()
                                      .filter(PhysicalTrainProxy.class::isInstance)
                                      .toList();
                              context
                                  .getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.translatable(
                                              "message.guidedrailways.identity.count",
                                              trains.size()),
                                      false);
                              for (var train : trains)
                                context.getSource().sendSuccess(() -> describe(train), false);
                              return trains.size();
                            })));
  }

  private NativeTrainDiagnostics() {}
}
