package com.guidedrailways;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CargoTransfers {
  private static final Map<Object, WeakReference<BlockEntity>> OWNERS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<ServerLevel, Map<UUID, CargoClock>> CLOCKS = new WeakHashMap<>();

  private CargoTransfers() {}

  static void observe(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
    container.addObserver(
        new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
          @Override
          public void onSubLevelRemoved(
              dev.ryanhcode.sable.sublevel.SubLevel body,
              dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
            // Unloading is not deletion: waiting schedules still need their transfer history.
            if (reason != dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED
                || !(body.getLevel() instanceof ServerLevel level)) return;
            var clocks = CLOCKS.get(level);
            if (clocks != null) clocks.remove(body.getUniqueId());
            CargoStorage.removed(level, body.getUniqueId());
          }
        });
  }

  public static boolean tracked(Object storage) {
    return OWNERS.containsKey(storage);
  }

  public static void bind(Object storage, BlockEntity owner) {
    if (storage != null) OWNERS.put(storage, new WeakReference<>(owner));
  }

  public static void exchanged(Object storage, boolean simulation, long amount) {
    if (simulation || amount <= 0) return;
    var reference = OWNERS.get(storage);
    var owner = reference == null ? null : reference.get();
    if (owner != null) {
      changed(owner);
      var peer = CargoAdapters.peer(owner);
      if (peer != null) changed(peer);
    }
  }

  public static void changed(BlockEntity owner) {
    if (owner != null
        && !owner.isRemoved()
        && owner.getLevel() instanceof ServerLevel level
        && level.hasChunkAt(owner.getBlockPos())
        && SableCompanion.INSTANCE.getContaining(level, owner.getBlockPos())
            instanceof ServerSubLevel body
        && !body.isRemoved()) {
      clock(level, body.getUniqueId()).exchanged(level.getGameTime(), false, 1);
      CargoStorage.invalidate(level, Set.of(body.getUniqueId()));
    }
  }

  static CargoClock clock(ServerLevel level, UUID body) {
    return CLOCKS
        .computeIfAbsent(level, k -> new HashMap<>())
        .computeIfAbsent(body, k -> new CargoClock());
  }

  static void unload(ServerLevel level) {
    CLOCKS.remove(level);
    OWNERS
        .entrySet()
        .removeIf(
            e -> {
              var owner = e.getValue().get();
              return owner == null || owner.getLevel() == level;
            });
  }
}
