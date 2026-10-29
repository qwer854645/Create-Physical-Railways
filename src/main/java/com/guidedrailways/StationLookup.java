package com.guidedrailways;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a station id still names a station in this world.
 *
 * <p>A schedule keeps the id of the station it is heading for, and that station can be broken while the train is
 * on its way. Nothing in the id changes when that happens, so the only way to notice is to ask the networks.
 */
final class StationLookup {
  static boolean exists(ServerLevel level, UUID station) {
    if (station == null) return false;
    for (var network : Create.RAILWAYS.trackNetworks.values())
      if (network.getPoint(EdgePointType.STATION, station) != null) return true;
    return false;
  }

  private StationLookup() {}
}
