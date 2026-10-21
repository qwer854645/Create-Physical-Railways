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
    var station = train.getCurrentStation();
    Object stoppedAt =
        station != null && station.getPresentTrain() == train
            ? station.name
            : Component.translatable("message.guidedrailways.identity.not_at_station");
    var description =
        Component.translatable(
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
                    : "message.guidedrailways.identity.on_track"),
            stoppedAt);
    if (train instanceof PhysicalTrainProxy proxy) {
      var journey = proxy.snapshot().getCompound("Journey");
      if (train.runtime.getSchedule() != null) {
        var nav = journey.getCompound("Navigation");
        var target =
            nav.hasUUID("Destination") && train.graph != null
                ? train.graph.getPoint(
                    com.simibubi.create.content.trains.graph.EdgePointType.STATION,
                    nav.getUUID("Destination"))
                : null;
        var status =
            Component.translatable("gui.guidedrailways.auto.state." + journey.getInt("Status"));
        description.append(
            Component.translatable(
                "message.guidedrailways.identity.journey",
                status,
                Math.min(
                    train.runtime.currentEntry + 1, train.runtime.getSchedule().entries.size()),
                train.runtime.getSchedule().entries.size(),
                target == null
                    ? Component.translatable("message.guidedrailways.identity.no_destination")
                    : target.name,
                target == null ? "—" : Math.round(nav.getDouble("Remaining") * 10) / 10.0,
                target == null ? 0 : nav.getIntArray("Path").length / 2));
        for (var prediction : train.runtime.submitPredictions()) {
          if (prediction.ticks != 0)
            description.append(
                prediction.ticks < 0
                    ? Component.translatable("message.guidedrailways.identity.eta_unknown")
                    : Component.translatable(
                        "message.guidedrailways.identity.eta", (prediction.ticks + 19L) / 20));
        }
      } else description.append(Component.translatable("message.guidedrailways.identity.manual"));
    }
    return description;
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
