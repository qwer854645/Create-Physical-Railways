package com.guidedrailways;

import com.google.common.collect.ImmutableMap;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Per-carriage native bulk query view. Never mounts, transfers or persists physical inventory. */
final class PhysicalCargoManager extends TrainCargoManager {
  private ServerLevel level;
  private UUID body;
  private Set<UUID> members = Set.of();
  private long cachedTick = Long.MIN_VALUE, boundAt;
  private TrainConsists.Group cachedGroup;
  private Object cachedCapture;
  private CargoStorage.Contents cached = CargoStorage.Contents.UNAVAILABLE;
  private boolean monitored;
  private int revision;
  private long transferVersion;

  PhysicalCargoManager() {
    super.initialize();
    items = new Items(this::contents);
    allItems = items;
    fuelItems = new Items(() -> CargoStorage.Contents.UNAVAILABLE);
    fluids = new Fluids(this::contents);
  }

  void bind(ServerLevel level, UUID body, Set<UUID> members) {
    if (this.level == level && Objects.equals(this.body, body) && this.members.equals(members))
      return;
    unbind();
    this.level = level;
    this.body = body;
    this.members = Set.copyOf(members);
    boundAt = level.getGameTime();
  }

  void unbind() {
    level = null;
    body = null;
    members = Set.of();
    cachedTick = Long.MIN_VALUE;
    cachedGroup = null;
    cachedCapture = null;
    cached = CargoStorage.Contents.UNAVAILABLE;
    monitored = false;
    transferVersion = 0;
    revision++;
  }

  boolean ready() {
    return contents().ready();
  }

  /**
   * False when this carriage's chunks are not loaded, so its contents cannot be inspected at all.
   *
   * <p>Such a carriage can never answer, and a caller that waits for every carriage to answer waits forever.
   */
  boolean loaded() {
    if (level == null) return false;
    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
    return container != null
        && container.getSubLevel(body) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel sublevel
        && !sublevel.getPlot().getLoadedChunks().isEmpty();
  }

  private CargoStorage.Contents contents() {
    if (level == null) return CargoStorage.Contents.UNAVAILABLE;
    long tick = level.getGameTime();
    var group = TrainConsists.get(level).group(body);
    long version = CargoTransfers.clock(level, body).version();
    if (cachedTick == tick
        && cachedGroup == group
        && version == transferVersion
        && cachedCapture == CargoStorage.captureToken(level, body)) return cached;
    cachedTick = tick;
    cachedGroup = group;
    CargoStorage.Contents next = CargoStorage.Contents.UNAVAILABLE;
    monitored = false;
    if (group != null && group.members().equals(members)) {
      var root = group.bodies().get(body);
      if (root != null && !root.isRemoved()) {
        var snapshot = CargoStorage.snapshot(level, root);
        if (snapshot.complete() && snapshot.bodies().equals(members)) {
          next = snapshot.byBody().getOrDefault(body, CargoStorage.Contents.UNAVAILABLE);
          monitored = snapshot.monitored();
        }
      }
    }
    if (!next.ready()) next = CargoStorage.Contents.UNAVAILABLE;
    if (!same(cached, next)) revision++;
    cached = next;
    cachedCapture = CargoStorage.captureToken(level, body);
    version = CargoTransfers.clock(level, body).version();
    if (version != transferVersion) {
      revision++;
      transferVersion = version;
    }
    return cached;
  }

  private static boolean same(CargoStorage.Contents a, CargoStorage.Contents b) {
    if (a == b) return true;
    if (a.ready() != b.ready()
        || !a.itemLimits().equals(b.itemLimits())
        || !a.fluidLimits().equals(b.fluidLimits())
        || a.items().size() != b.items().size()
        || a.fluids().size() != b.fluids().size()) return false;
    for (int i = 0; i < a.items().size(); i++)
      if (!ItemStack.matches(a.items().get(i), b.items().get(i))) return false;
    for (int i = 0; i < a.fluids().size(); i++)
      if (!FluidStack.matches(a.fluids().get(i), b.fluids().get(i))) return false;
    return true;
  }

  @Override
  public int getVersion() {
    contents();
    return revision;
  }

  @Override
  public int getTicksSinceLastExchange() {
    if (!ready() || !monitored) return 0;
    long last = Math.max(boundAt, CargoTransfers.clock(level, body).last());
    return (int) Math.clamp(level.getGameTime() - last, 0, Integer.MAX_VALUE);
  }

  @Override
  public void initialize() {}

  @Override
  public void resetIdleCargoTracker() {}

  @Override
  public void tickIdleCargoTracker() {}

  @Override
  public void attachExternal(IItemHandlerModifiable handler) {}

  @Override
  public void addBlock(
      Level level, BlockState state, BlockPos pos, BlockPos local, BlockEntity entity) {}

  @Override
  public void unmount(Level level, StructureBlockInfo info, BlockPos pos, BlockEntity entity) {}

  @Override
  public void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {}

  @Override
  public void read(
      CompoundTag tag, HolderLookup.Provider registries, boolean client, Contraption contraption) {}

  @Override
  public void tick(AbstractContraptionEntity entity) {}

  @Override
  public void handleSync(MountedStorageSyncPacket packet, AbstractContraptionEntity entity) {}

  @Override
  public boolean handlePlayerStorageInteraction(
      Contraption contraption, Player player, BlockPos pos) {
    return false;
  }

  static final class Items extends MountedItemStorageWrapper {
    private final Supplier<CargoStorage.Contents> source;

    Items(Supplier<CargoStorage.Contents> source) {
      super(ImmutableMap.of());
      this.source = source;
    }

    // The native wrapper constructor calls getSlots before source is assigned.
    private CargoStorage.Contents contents() {
      return source == null ? CargoStorage.Contents.UNAVAILABLE : source.get();
    }

    @Override
    public int getSlots() {
      return contents().items().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      var list = contents().items();
      return slot < 0 || slot >= list.size() ? ItemStack.EMPTY : list.get(slot).copy();
    }

    @Override
    public int getSlotLimit(int slot) {
      var list = contents().itemLimits();
      return slot < 0 || slot >= list.size() ? 0 : list.get(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return false;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {}

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return ItemStack.EMPTY;
    }
  }

  static final class Fluids extends MountedFluidStorageWrapper {
    private final Supplier<CargoStorage.Contents> source;

    Fluids(Supplier<CargoStorage.Contents> source) {
      super(ImmutableMap.of());
      this.source = source;
    }

    // The native wrapper constructor calls getSlots before source is assigned.
    private CargoStorage.Contents contents() {
      return source == null ? CargoStorage.Contents.UNAVAILABLE : source.get();
    }

    @Override
    public int getTanks() {
      return contents().fluids().size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
      var list = contents().fluids();
      return tank < 0 || tank >= list.size() ? FluidStack.EMPTY : list.get(tank).copy();
    }

    @Override
    public int getTankCapacity(int tank) {
      var list = contents().fluidLimits();
      return tank < 0 || tank >= list.size() ? 0 : list.get(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
      return false;
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
      return 0;
    }

    @Override
    public FluidStack drain(FluidStack stack, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
      return FluidStack.EMPTY;
    }
  }
}
