package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Native postboxes exchange real stacks only after a verified physical arrival. */
final class PackageMail {
  record Selection(Set<UUID> stations, PackageTask.Decision decision) {}

  private record Port(PostboxBlockEntity entity, String address) {}

  private PackageMail() {}

  static Selection targets(ServerLevel level, ScheduleController controller) {
    boolean delivery = controller.instruction() instanceof DeliverPackagesInstruction;
    var snapshot = CargoStorage.snapshot(level, DriverControls.body(controller.owner));
    if (!snapshot.complete()) return new Selection(Set.of(), PackageTask.Decision.UNLOADED);
    var parcels = snapshot.items().stream().filter(PackageItem::isPackage).toList();
    Pattern filter =
        controller.instruction() instanceof FetchPackagesInstruction fetch
            ? Pattern.compile(fetch.getFilterForRegex())
            : null;
    var ids = new HashSet<UUID>();
    boolean complete = true;
    for (var graph : Create.RAILWAYS.sided(level).trackNetworks.values())
      for (var station : graph.getPoints(EdgePointType.STATION)) {
        if (!level.dimension().equals(station.getBlockEntityDimension())) continue;
        if (!RailDocks.bound(level, station.id)) continue;
        for (var entry : station.connectedPorts.entrySet()) {
          var port = entry.getValue();
          if (CargoStorage.trainBody(level, entry.getKey()) != null) continue;
          if (delivery) {
            if (parcels.stream().anyMatch(p -> PackageItem.matchAddress(p, port.address)))
              ids.add(station.id);
          } else {
            var box = loadedBox(level, station, entry.getKey());
            if (box == null) complete = false;
            // Offline buffers are read-only hints; unloaded ports cannot justify skipping work.
            var inventory = box == null ? port.offlineBuffer : box.inventory;
            for (int i = 0; i < inventory.getSlots(); i++) {
              var stack = inventory.getStackInSlot(i);
              if (PackageItem.isPackage(stack)
                  && !PackageItem.matchAddress(stack, port.address)
                  && filter.matcher(PackageItem.getAddress(stack)).matches()) {
                ids.add(station.id);
                break;
              }
            }
          }
        }
      }
    return new Selection(
        Set.copyOf(ids), PackageTask.decide(delivery, complete, !parcels.isEmpty(), ids));
  }

  private static PostboxBlockEntity loadedBox(
      ServerLevel level, GlobalStation station, net.minecraft.core.BlockPos pos) {
    if (!level.hasChunkAt(pos)
        || !(level.getBlockEntity(pos) instanceof PostboxBlockEntity box)
        || box.isRemoved()) return null;
    return box.trackedGlobalStation.get() == station ? box : null;
  }

  static int exchange(ServerLevel level, ScheduleController controller) {
    controller.mailMonitored = false;
    if (controller.station == null) return ScheduleController.UNLOADED;
    GlobalStation stop = null;
    for (var graph : Create.RAILWAYS.sided(level).trackNetworks.values()) {
      stop = graph.getPoint(EdgePointType.STATION, controller.station);
      if (stop != null) break;
    }
    if (stop == null || !level.dimension().equals(stop.getBlockEntityDimension()))
      return ScheduleController.UNLOADED;
    var present = stop.getPresentTrain();
    if (present != null) {
      var body = DriverControls.body(controller.owner);
      var group = body == null ? null : TrainConsists.get(level).group(body.getUniqueId());
      if (NativeTrainBridge.registered(level, group) != present)
        return ScheduleController.PACKAGE_WAIT;
    }
    var snapshot = CargoStorage.snapshot(level, DriverControls.body(controller.owner));
    if (!snapshot.complete()
        || controller.cargoBodies != null && !controller.cargoBodies.equals(snapshot.bodies()))
      return ScheduleController.UNLOADED;
    var ports = new ArrayList<Port>();
    for (var entry : stop.connectedPorts.entrySet()) {
      var box = loadedBox(level, stop, entry.getKey());
      if (box == null) return ScheduleController.UNLOADED;
      // A postbox on the train itself cannot be a station-side transfer endpoint.
      if (CargoStorage.trainBody(level, box.getBlockPos()) != null) continue;
      ports.add(new Port(box, entry.getValue().address));
    }
    if (ports.isEmpty()) return ScheduleController.WAITING;
    if (!RailDocks.bound(level, stop.id))
      return controller.packageInstruction()
          ? ScheduleController.RAIL_DOCK
          : ScheduleController.WAITING;
    int docking = RailDocks.acquire(level, controller, snapshot);
    if (docking != ScheduleController.WAITING) return docking;
    controller.mailMonitored = true;
    var stores = CargoStorage.packageInventories(level, snapshot);
    var cargo = new ArrayList<Store>();
    for (var access : stores)
      cargo.add(
          new Store(
              access.inventory(),
              () -> {
                access.owner().setChanged();
                CargoTransfers.changed(access.owner());
              }));
    int received = 0, sent = 0;
    // Unload first, leaving space for outgoing mail at either endpoint.
    for (var port : ports) {
      var stationStore = mailbox(port.entity);
      for (var inventory : cargo)
        received +=
            ParcelTransfer.move(
                inventory,
                List.of(stationStore),
                p -> PackageItem.matchAddress(p, port.address),
                64 - received);
    }
    for (var port : ports) {
      var stationStore = mailbox(port.entity);
      sent +=
          ParcelTransfer.move(
              stationStore,
              cargo,
              p -> PackageItem.isPackage(p) && !PackageItem.matchAddress(p, port.address),
              64 - sent);
    }
    if (received + sent > 0) {
      CargoStorage.invalidate(level, snapshot.bodies());
      Create.RAILWAYS.markTracksDirty();
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[PackageMail] console={} station={} loaded={} delivered={} bodies={}",
              controller.owner.getBlockPos(),
              stop.id,
              sent,
              received,
              snapshot.bodies().size());
    }
    // Do not depart a package task while matching mail is blocked by a full/locked store.
    if (controller.packageInstruction()) {
      var remaining = CargoStorage.snapshot(level, DriverControls.body(controller.owner));
      if (!remaining.complete()) return ScheduleController.UNLOADED;
      for (var port : ports) {
        if (remaining.items().stream()
            .anyMatch(
                stack ->
                    PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, port.address)))
          return ScheduleController.PACKAGE_WAIT;
        for (int i = 0; i < port.entity.inventory.getSlots(); i++) {
          var stack = port.entity.inventory.getStackInSlot(i);
          if (PackageItem.isPackage(stack) && !PackageItem.matchAddress(stack, port.address))
            return ScheduleController.PACKAGE_WAIT;
        }
      }
    }
    return ScheduleController.WAITING;
  }

  private static Store mailbox(PostboxBlockEntity box) {
    return new Store(box.inventory, box::setChanged) {
      @Override
      public void set(int slot, ItemStack parcel) {
        var old = inventory.getStackInSlot(slot).copy();
        super.set(slot, parcel);
        if (box.computerBehaviour != null) {
          var event =
              new com.simibubi.create.compat.computercraft.events.PackageEvent(
                  parcel == null ? old : parcel.copy(),
                  parcel == null ? "package_sent" : "package_received");
          box.computerBehaviour.prepareComputerEvent(event);
        }
        box.spawnParticles();
      }
    };
  }

  private static class Store implements ParcelTransfer.Slots<ItemStack> {
    final IItemHandlerModifiable inventory;
    private final Runnable changed;

    Store(IItemHandlerModifiable inventory, Runnable changed) {
      this.inventory = inventory;
      this.changed = changed;
    }

    public int size() {
      return inventory.getSlots();
    }

    public ItemStack get(int slot) {
      var stack = inventory.getStackInSlot(slot);
      return stack.isEmpty() ? null : stack.copy();
    }

    public boolean canTake(int slot, ItemStack parcel) {
      return PackageItem.isPackage(parcel)
          && ItemStack.matches(parcel, inventory.extractItem(slot, parcel.getCount(), true));
    }

    public boolean accepts(int slot, ItemStack parcel) {
      return inventory.isItemValid(slot, parcel)
          && inventory.insertItem(slot, parcel.copy(), true).isEmpty();
    }

    public void set(int slot, ItemStack parcel) {
      inventory.setStackInSlot(slot, parcel == null ? ItemStack.EMPTY : parcel.copy());
      changed.run();
    }
  }
}
