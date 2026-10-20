package com.guidedrailways;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

final class TrainPassengers {
  private TrainPassengers() {}

  static PassengerManifest snapshot(ServerLevel level, ServerSubLevel root) {
    var group = CargoStorage.consist(level, root);
    boolean complete = group.complete();
    for (var body : group.bodies().values())
      if (body.isRemoved() || body.getPlot().getLoadedChunks().isEmpty()) complete = false;
    var seated = new java.util.HashMap<java.util.UUID, java.util.UUID>();
    // Sable keeps the SeatEntity at its seat's plot position while transforming its rider.
    // Use that anchor, never the player's world position or proximity to the train.
    for (var player : level.players()) {
      if (!player.isAlive()
          || player.isSpectator()
          || !(player.getVehicle() instanceof SeatEntity seat)
          || seat.isRemoved()) continue;
      var pos = seat.blockPosition();
      if (!(SableCompanion.INSTANCE.getContaining(level, pos) instanceof ServerSubLevel body)
          || !group.bodies().containsKey(body.getUniqueId())) continue;
      if (!level.hasChunkAt(pos)) {
        complete = false;
        continue;
      }
      if (level.getBlockState(pos).getBlock() instanceof SeatBlock)
        seated.put(player.getUUID(), body.getUniqueId());
    }
    var result = new PassengerManifest(group.bodies().keySet(), complete);
    seated.forEach(result::seated);
    return result;
  }
}
