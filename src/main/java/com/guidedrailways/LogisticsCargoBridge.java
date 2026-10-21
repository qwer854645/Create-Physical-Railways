package com.guidedrailways;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional CAAL cargo integration. Its route recorder and flight controller are untouched. */
public final class LogisticsCargoBridge {
  private LogisticsCargoBridge() {}

  public static Object stamp(BlockEntity connector, Object contents) {
    if (!(connector.getLevel() instanceof ServerLevel level)) return contents;
    CargoAdapters.bindConnector(connector);
    try {
      if (!(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(
              level, connector.getBlockPos())
          instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)) return contents;
      var clock = CargoTransfers.clock(level, body.getUniqueId());
      var itemState = new ArrayList<Object>((List<?>) CargoAdapters.call(contents, "items"));
      itemState.add("guidedrailways:exchange=" + clock.version());
      return contents
          .getClass()
          .getConstructor(List.class, List.class, List.class, List.class, int.class, long.class)
          .newInstance(
              itemState,
              CargoAdapters.call(contents, "fluids"),
              CargoAdapters.call(contents, "itemStacks"),
              CargoAdapters.call(contents, "fluidStacks"),
              CargoAdapters.call(contents, "itemCount"),
              CargoAdapters.call(contents, "fluidAmount"));
    } catch (ReflectiveOperationException error) {
      CargoAdapters.warn(connector, error);
      return contents;
    }
  }
}
