package com.guidedrailways;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Read live containers; no inventory is moved, copied back or exposed for insertion. */
public final class CargoStorage {
  public record Location(BlockPos pos, boolean items, boolean fluids, boolean dock) {}

  record Consist(Map<UUID, ServerSubLevel> bodies, boolean complete) {}

  public record Snapshot(
      Set<UUID> bodies,
      boolean complete,
      boolean monitored,
      List<ItemStack> items,
      List<FluidStack> fluids,
      List<Location> locations,
      long captured) {
    long items(ServerLevel level, FilterItemStack filter, boolean stacks) {
      long n = 0;
      for (var s : items)
        if (filter.test(level, s))
          n += CargoClock.itemAmount(s.getCount(), s.getMaxStackSize(), stacks);
      return n;
    }

    long fluids(ServerLevel level, FilterItemStack filter) {
      long n = 0;
      for (var f : fluids) if (filter.test(level, f)) n += f.getAmount();
      return n;
    }
  }

  private static final class Cached {
    final Map<UUID, Snapshot> snapshots = new HashMap<>();
    long nextCleanup;
  }

  private static final Map<ServerLevel, Cached> CACHE = new WeakHashMap<>();

  static void tick(ServerLevel level) {
    var cached = CACHE.get(level);
    if (cached == null || level.getGameTime() < cached.nextCleanup) return;
    long tick = level.getGameTime();
    cached.nextCleanup = tick + 20;
    cached.snapshots.values().removeIf(s -> tick - s.captured > 200);
  }

  static void removed(ServerLevel level, UUID body) {
    var cached = CACHE.get(level);
    if (cached != null) cached.snapshots.values().removeIf(s -> s.bodies.contains(body));
  }

  private CargoStorage() {}

  static Consist consist(ServerLevel level, ServerSubLevel root) {
    var bodies = new HashMap<UUID, ServerSubLevel>();
    if (root == null || root.isRemoved()) return new Consist(bodies, false);
    bodies.put(root.getUniqueId(), root);
    var group = TrainConsists.get(level).group(root.getUniqueId());
    if (group != null) bodies.putAll(group.bodies());
    return new Consist(bodies, CouplerConnections.cargoComplete(level, bodies.keySet()));
  }

  public static Snapshot snapshot(ServerLevel level, ServerSubLevel root) {
    var group = consist(level, root);
    var ids = Set.copyOf(group.bodies.keySet());
    long tick = level.getGameTime();
    if (!group.complete)
      return new Snapshot(ids, false, false, List.of(), List.of(), List.of(), tick);
    var cache = CACHE.computeIfAbsent(level, k -> new Cached()).snapshots;
    var old = root == null ? null : cache.get(root.getUniqueId());
    if (old != null
        && old.complete
        && old.bodies.equals(ids)
        && tick >= old.captured
        && tick - old.captured < 20) {
      boolean loaded = true;
      for (var location : old.locations)
        if (!level.hasChunkAt(location.pos) || level.getBlockEntity(location.pos) == null) {
          loaded = false;
          break;
        }
      if (loaded) return old;
      return new Snapshot(ids, false, old.monitored, List.of(), List.of(), List.of(), tick);
    }
    var items = new ArrayList<ItemStack>();
    var fluids = new ArrayList<FluidStack>();
    var locations = new ArrayList<Location>();
    var roots = new HashSet<BlockPos>();
    var itemHandlers = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    var fluidHandlers = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    boolean complete = true, monitored = false, monitorFailed = false;
    for (var body : group.bodies.values()) {
      if (body.isRemoved() || body.getPlot().getLoadedChunks().isEmpty()) {
        complete = false;
        continue;
      }
      for (var holder : body.getPlot().getLoadedChunks())
        for (var pendingPos : List.copyOf(holder.getChunk().getBlockEntitiesPos())) {
          // Include block entities still represented by packed NBT after plot load.
          var raw = holder.getChunk().getBlockEntity(pendingPos);
          if (raw == null || raw.isRemoved() || !level.hasChunkAt(raw.getBlockPos())) {
            complete = false;
            continue;
          }
          BlockEntity entity = raw;
          if (entity instanceof IMultiBlockEntityContainer multi) {
            var controller = multi.getController();
            if (controller == null
                || !body.getPlot().contains(new net.minecraft.world.level.ChunkPos(controller))
                || !level.hasChunkAt(controller)) {
              complete = false;
              continue;
            }
            entity = level.getBlockEntity(controller);
            if (entity == null) {
              complete = false;
              continue;
            }
          }
          var pos = entity.getBlockPos().immutable();
          if (!roots.add(pos)) continue;
          boolean hasItems = false, hasFluids = false;
          try {
            IItemHandler inventory = CargoAdapters.localItems(entity);
            IFluidHandler tank = CargoAdapters.localFluids(entity);
            boolean special = CargoAdapters.dock(entity) || CargoAdapters.hose(entity);
            if (!special) {
              if (entity instanceof Container container) inventory = new InvWrapper(container);
              else
                inventory =
                    level.getCapability(Capabilities.ItemHandler.BLOCK, pos, (Direction) null);
              tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, (Direction) null);
              for (var side : Direction.values()) {
                if (inventory == null)
                  inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                if (tank == null)
                  tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
              }
            }
            if (inventory != null && itemHandlers.add(inventory)) {
              hasItems = true;
              CargoTransfers.bind(inventory, entity);
              for (int i = 0; i < inventory.getSlots(); i++)
                items.add(inventory.getStackInSlot(i).copy());
            }
            if (tank != null && fluidHandlers.add(tank)) {
              hasFluids = true;
              CargoTransfers.bind(tank, entity);
              for (int i = 0; i < tank.getTanks(); i++) fluids.add(tank.getFluidInTank(i).copy());
            }
            if (hasItems || hasFluids) {
              locations.add(new Location(pos, hasItems, hasFluids, CargoAdapters.dock(entity)));
              if (special) {
                if (CargoHookStatus.ready(CargoAdapters.dock(entity) ? "dock" : "hose"))
                  monitored = true;
                else monitorFailed = true;
              }
            }
          } catch (RuntimeException error) {
            complete = false;
            CargoAdapters.warn(entity, error);
          }
        }
    }
    var result =
        new Snapshot(
            ids,
            complete,
            monitored && !monitorFailed,
            List.copyOf(items),
            List.copyOf(fluids),
            List.copyOf(locations),
            tick);
    if (old != null
        && old.complete
        && complete
        && old.bodies.equals(ids)
        && !sameContents(old, result))
      for (var id : ids) CargoTransfers.clock(level, id).exchanged(tick, false, 1);
    for (var id : ids) cache.put(id, result);
    return result;
  }

  private static boolean sameContents(Snapshot a, Snapshot b) {
    if (!a.locations.equals(b.locations)
        || a.items.size() != b.items.size()
        || a.fluids.size() != b.fluids.size()) return false;
    for (int i = 0; i < a.items.size(); i++)
      if (!ItemStack.matches(a.items.get(i), b.items.get(i))) return false;
    for (int i = 0; i < a.fluids.size(); i++)
      if (!FluidStack.matches(a.fluids.get(i), b.fluids.get(i))) return false;
    return true;
  }

  static boolean idle(
      ServerLevel level, Snapshot snapshot, long since, long duration, boolean mailMonitored) {
    if (!snapshot.complete || !snapshot.monitored && !mailMonitored) return false;
    for (var id : snapshot.bodies)
      if (!CargoTransfers.clock(level, id).idle(level.getGameTime(), since, duration)) return false;
    return true;
  }

  record ItemAccess(BlockEntity owner, IItemHandlerModifiable inventory) {}

  static List<ItemAccess> packageInventories(ServerLevel level, Snapshot snapshot) {
    var out = new ArrayList<ItemAccess>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    for (var location : snapshot.locations)
      if (location.items && !location.dock && level.hasChunkAt(location.pos)) {
        var entity = level.getBlockEntity(location.pos);
        if (entity == null || entity.isRemoved()) continue;
        IItemHandler inventory;
        if (CargoAdapters.hose(entity)) inventory = CargoAdapters.localItems(entity);
        else if (entity instanceof Container container) inventory = new InvWrapper(container);
        else {
          inventory =
              level.getCapability(Capabilities.ItemHandler.BLOCK, location.pos, (Direction) null);
          for (var side : Direction.values())
            if (inventory == null)
              inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, location.pos, side);
        }
        if (inventory instanceof IItemHandlerModifiable writable && seen.add(inventory))
          out.add(new ItemAccess(entity, writable));
      }
    return out;
  }

  static void invalidate(ServerLevel level, Set<UUID> bodies) {
    var cache = CACHE.get(level);
    if (cache != null) for (var id : bodies) cache.snapshots.remove(id);
  }

  static boolean blockedByDock(ServerLevel level, ServerSubLevel root) {
    var snapshot = snapshot(level, root);
    for (var location : snapshot.locations)
      if (location.dock && level.hasChunkAt(location.pos)) {
        var connector = level.getBlockEntity(location.pos);
        if (connector == null) continue;
        try {
          if (!(boolean) CargoAdapters.call(connector, "isLocked")) continue;
        } catch (ReflectiveOperationException error) {
          CargoAdapters.warn(connector, error);
          return true;
        }
        var peer = CargoAdapters.peer(connector);
        if (peer == null
            || !(SableCompanion.INSTANCE.getContaining(level, peer.getBlockPos())
                instanceof ServerSubLevel other)
            || !snapshot.bodies.contains(other.getUniqueId())) return true;
      }
    return false;
  }

  public static ServerSubLevel trainBody(ServerLevel level, BlockPos pos) {
    if (!(SableCompanion.INSTANCE.getContaining(level, pos) instanceof ServerSubLevel body))
      return null;
    var group = TrainConsists.get(level).group(body.getUniqueId());
    return group == null || group.units().isEmpty() ? null : group.bodies().get(body.getUniqueId());
  }

  static void topologyChanged(ServerLevel level) {
    CACHE.remove(level);
  }

  static void unload(ServerLevel level) {
    CACHE.remove(level);
    CargoTransfers.unload(level);
  }
}
