package com.guidedrailways;

import net.minecraft.server.level.ServerLevel;

/** Optional electric supply; core physics remains loadable without CEE. */
final class TrainElectricity {
  interface Bridge {
    void refresh(ServerLevel level);

    void unload(ServerLevel level);
  }

  static Bridge bridge;

  static void refresh(ServerLevel level) {
    if (bridge != null) bridge.refresh(level);
  }

  static void unload(ServerLevel level) {
    if (bridge != null) bridge.unload(level);
  }

  private TrainElectricity() {}
}
