package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableNBTUtils;
import java.util.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

/** Server-thread physical consist transfer. Independently implemented on the Sable serialization API. */
public final class PhysicalTransfer {
  static final int MAX_BODIES = 128, MAX_BLOCKS = 32768, MAX_VOLUME = 65536, MAX_CHUNKS = 64, MAX_GLUE = 256;
  private record Context(ServerLevel source, ServerLevel target, Set<UUID> ids) {}
  private static Context active;
  public record Result(UUID operation, int bodies) {}
  private record Part(ServerSubLevel body, CompoundTag original, CompoundTag destination) {}
  private record Glue(UUID owner, SuperGlueEntity entity, CompoundTag data) {}

  private PhysicalTransfer() {}

  static boolean preservesIdentity(ServerLevel level, UUID id) {
    return active != null && (active.source == level || active.target == level) && active.ids.contains(id);
  }

  private static IllegalArgumentException reject(String reason) {
    return new IllegalArgumentException("message.guidedrailways.transfer." + reason);
  }

  /** Destination is the root body's pose position, with orientation and internal coordinates retained. */
  public static Result transfer(ServerLevel source, UUID bodyId, ServerLevel target, Vec3 destination) {
    return transfer(source, bodyId, target, destination, 0, false);
  }

  static Result throughPortal(ServerLevel source, UUID bodyId, ServerLevel target, Vec3 destination, int turns) {
    return transfer(source, bodyId, target, destination, turns, true);
  }

  private static Result transfer(ServerLevel source, UUID bodyId, ServerLevel target, Vec3 destination,
      int turns, boolean portal) {
    if (!source.getServer().isSameThread() || active != null
        || SubLevelPhysicsSystem.currentlySteppingSystem != null) throw reject("busy");
    if (source == target || source.getServer() != target.getServer()
        || !Double.isFinite(destination.x) || !Double.isFinite(destination.y)
        || !Double.isFinite(destination.z)) throw reject("destination");
    var src = SubLevelContainer.getContainer(source);
    var dst = SubLevelContainer.getContainer(target);
    var physics = SubLevelPhysicsSystem.get(source);
    if (src == null || dst == null || physics == null || SubLevelPhysicsSystem.get(target) == null)
      throw reject("unavailable");
    if (src.getLogPlotSize() != dst.getLogPlotSize() || src.getLogSideLength() != dst.getLogSideLength()
        || !src.getOrigin().equals(dst.getOrigin())) throw reject("layout");
    if (!(src.getSubLevel(bodyId) instanceof ServerSubLevel root) || root.isRemoved()) throw reject("missing");
    // Selecting the lower bogey also selects its owning carriage, never just half a support.
    if (source.getBlockState(root.getPlot().getCenterBlock()).is(Content.BOGIE.get())) {
      var owner = BogieMountBinding.owner(root.getUserDataTag());
      if (owner == null || !owner.location().dimension().equals(source.dimension().location().toString()))
        throw reject("incomplete");
      var mount = source.getBlockEntity(BlockPos.of(owner.location().position()));
      if (!(mount instanceof MountEntity plate) || !bodyId.equals(plate.bogieId)
          || !(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(source, plate.getBlockPos())
              instanceof ServerSubLevel carrier)) throw reject("incomplete");
      root = carrier;
    }
    var members = TransferGroup.collect(source, root);
    var mounts = new ArrayList<MountEntity>();
    for (var carrier : List.copyOf(members.values())) for (var be : blockEntities(carrier)) {
      if (be instanceof MountEntity mount) {
        if (mount.bogieId == null || mount.handoff != null
            || !(src.getSubLevel(mount.bogieId) instanceof ServerSubLevel lower) || lower.isRemoved()
            || lower == carrier || !BogieMountBinding.matches(lower.getUserDataTag(),
                VehiclePhysics.mountLocation(source, mount.getBlockPos()))) throw reject("incomplete");
        if (members.get(lower.getUniqueId()) != lower) throw reject("incomplete");
        mounts.add(mount);
      }
    }
    if (members.size() > MAX_BODIES) throw reject("limit");
    var ids = Set.copyOf(members.keySet());
    TransferRecovery.check(source.getServer(), ids);
    var identities = TrainIdentityData.get(source).ledger.records.values().stream()
        .filter(identity -> identity.members().stream().anyMatch(ids::contains)).toList();
    var targetIdentities = TrainIdentityData.get(target).ledger.records;
    for (var identity : identities)
      if (!ids.containsAll(identity.members()) || targetIdentities.containsKey(identity.id())
          || targetIdentities.values().stream().anyMatch(other -> other.members().stream().anyMatch(ids::contains)))
        throw reject("incomplete");
    var anchor = root.logicalPose().position();
    var transform = new PortalTransform(new Vec3(anchor.x, anchor.y, anchor.z), destination, turns);
    var moves = new LinkedHashMap<UUID, TransferPlots.Move>();
    var occupied = (BitSet) dst.getOccupancy().clone();
    int side = 1 << dst.getLogSideLength();
    for (var body : members.values()) {
      if (dst.getSubLevel(body.getUniqueId()) != null) throw reject("occupied");
      int x = body.getPlot().plotPos.x - src.getOrigin().x, z = body.getPlot().plotPos.z - src.getOrigin().y;
      int index = TransferPlots.reserve(occupied, dst.getIndex(x, z), Math.multiplyExact(side, side));
      moves.put(body.getUniqueId(), new TransferPlots.Move(x, z, index & (side - 1), index >>> dst.getLogSideLength(),
          src.getOrigin().x, src.getOrigin().y, src.getLogPlotSize() + 4));
    }
    var links = new TransferLinks(source, target, members.values(), moves.values());
    var loadedChunks = new HashSet<net.minecraft.world.level.ChunkPos>();
    var passengers = new TransferPassengers(source, target, members, moves, transform, loadedChunks);
    var parts = new ArrayList<Part>();
    var glues = new LinkedHashMap<UUID, Glue>();
    int blocks = 0;
    for (var body : members.values()) {
      if (physics.getPipeline().getLinearVelocity(body, new Vector3d()).lengthSquared()
          > (portal ? PhysicalPortals.MAX_SPEED * PhysicalPortals.MAX_SPEED : .0025)
          || physics.getPipeline().getAngularVelocity(body, new Vector3d()).lengthSquared() > (portal ? .25 : .0001))
        throw reject(portal ? "portal_speed" : "moving");
      for (var actor : body.getPlot().getBlockEntityActors()) {
        for (var dependencies : java.util.Arrays.asList(actor.sable$getConnectionDependencies(), actor.sable$getLoadingDependencies())) {
          if (dependencies != null) for (var dependency : dependencies)
            if (dependency == null || !ids.contains(dependency.getUniqueId())) throw reject("unsupported");
        }
      }
      if (!body.getPlot().getContraptions().isEmpty()) throw reject("unsupported");
      var box = body.boundingBox().toMojang();
      double volume = box.getXsize() * box.getYsize() * box.getZsize();
      if (!Double.isFinite(volume) || volume <= 0 || volume > MAX_VOLUME) throw reject("limit");
      var internal = body.getPlot().getBoundingBox();
      var plotBox = new AABB(internal.minX(), internal.minY(), internal.minZ(),
          internal.maxX() + 1, internal.maxY() + 1, internal.maxZ() + 1);
      var entities = new HashSet<>(source.getEntities(null, box.inflate(.05)));
      entities.addAll(source.getEntities(null, plotBox));
      for (var entity : entities) {
        if (passengers.owns(entity)) continue;
        if (!(entity instanceof SuperGlueEntity glue) || !plotBox.intersects(glue.getBoundingBox()))
          throw reject("entities");
        var data = new CompoundTag();
        if (!glue.saveAsPassenger(data) || target.getEntity(glue.getUUID()) != null) throw reject("occupied");
        glues.put(glue.getUUID(), new Glue(body.getUniqueId(), glue, data));
        if (glues.size() > MAX_GLUE) throw reject("limit");
      }
      var arrival = transform.bounds(box);
      if (arrival.minY < target.getMinBuildHeight() || arrival.maxY > target.getMaxBuildHeight()
          || !target.getWorldBorder().isWithinBounds(BlockPos.containing(arrival.minX, arrival.minY, arrival.minZ))
          || !target.getWorldBorder().isWithinBounds(BlockPos.containing(arrival.maxX, arrival.maxY, arrival.maxZ)))
        throw reject("destination");
      loadArea(target, arrival, loadedChunks);
      if (!target.noCollision(arrival) || !target.getEntities(null, arrival).isEmpty()
          || dst.getAllSubLevels().stream().anyMatch(other -> !other.isRemoved()
              && other.boundingBox().toMojang().intersects(arrival))) throw reject("blocked");
      if (portal && source.getBlockState(body.getPlot().getCenterBlock()).is(Content.BOGIE.get())
          && target.noCollision(arrival.move(0, -1, 0))) throw reject("portal_landing");
      for (var holder : body.getPlot().getLoadedChunks())
        for (var section : holder.getChunk().getSections()) {
          int[] count = {0};
          section.getStates().count((state, amount) -> { if (!state.isAir()) count[0] += amount; });
          blocks += count[0];
          if (blocks > MAX_BLOCKS) throw reject("limit");
        }
      for (var be : blockEntities(body)) validateBlockEntity(source, be, portal);
      var original = SubLevelSerializer.toData(body, List.of()).fullTag().copy();
      var copy = original.copy();
      var plot = copy.getCompound("plot");
      var move = moves.get(body.getUniqueId());
      TransferPlots.apply(copy, move, moves.values());
      TransferSections.shift(plot, source.getMinSection() - target.getMinSection(), target.getSectionsCount());
      var pose = SableNBTUtils.readPose3d(copy.getCompound("pose"));
      pose.rotationPoint().add(move.dx(), 0, move.dz());
      var moved = transform.point(new Vec3(pose.position().x, pose.position().y, pose.position().z));
      pose.position().set(moved.x, moved.y, moved.z);
      pose.orientation().premul(new org.joml.Quaterniond().rotationY(turns * Math.PI / 2));
      copy.put("pose", SableNBTUtils.writePose3d(pose));
      copy.put("world_bounds", SableNBTUtils.writeBoundingBox(new BoundingBox3d(
          arrival.minX, arrival.minY, arrival.minZ, arrival.maxX, arrival.maxY, arrival.maxZ)));
      copy.put("linear_velocity", SableNBTUtils.writeVector3d(new Vector3d()));
      copy.put("angular_velocity", SableNBTUtils.writeVector3d(new Vector3d()));
      if (portal) {
        var data = copy.getCompound("user_data");
        data.putLong("GuidedPortalUntil", source.getServer().overworld().getGameTime() + PhysicalPortals.COOLDOWN_TICKS);
        copy.put("user_data", data);
      }
      TransferControls.park(copy);
      if (source.getBlockState(body.getPlot().getCenterBlock()).is(Content.BOGIE.get())) {
        var data = copy.getCompound("user_data");
        var owner = BogieMountBinding.owner(data);
        if (owner == null) throw reject("incomplete");
        var next = owner.moveTo(owner.location(), new MountOwnership.Location(
            target.dimension().location().toString(), TransferPlots.point(moves.values(),
                BlockPos.of(owner.location().position())).asLong()), owner.revision());
        if (!next.changed()) throw reject("incomplete");
        var claim = BogieMountBinding.encode(next.ownership().location());
        claim.putLong("Revision", next.ownership().revision());
        data.put(BogieMountBinding.KEY, claim);
        data.putBoolean("GuidedDerailed", true);
        copy.put("user_data", data);
      }
      resetKinetics(copy);
      parts.add(new Part(body, original, copy));
    }
    // Preloading may discover dormant destination bodies. A reservation is only a plan until load().
    for (var entry : moves.entrySet()) {
      var move = entry.getValue();
      if (dst.getSubLevel(entry.getKey()) != null || dst.getOccupancy().get(dst.getIndex(move.newX(), move.newZ()))) throw reject("occupied");
    }
    UUID operation = UUID.randomUUID();
    var backup = TransferBackup.prepare(source, target, operation,
        parts.stream().map(Part::original).toList(), parts.stream().map(Part::destination).toList(),
        glues.values().stream().map(glue -> {
          var tag = new CompoundTag(); tag.putUUID("Owner", glue.owner); tag.put("Data", glue.data);
          var after = glue.data.copy(); TransferPlots.entity(after, moves.get(glue.owner)); tag.put("NextData", after); return tag;
        }).toList(), links.snapshot(), identities, passengers.snapshot(), parts.stream().map(part -> {
          var tag = new CompoundTag();
          var old = part.body.getLastSerializationPointer();
          if (old != null) tag.putLong("SavedSource", old.chunkPos().toLong());
          tag.putLong("Source", holdingChunk(part.original).toLong()); tag.putLong("Target", holdingChunk(part.destination).toLong());
          return tag;
        }).toList(), portal ? root.getUniqueId() : null);
    TransferRecovery.lock(source.getServer(), ids, operation);
    active = new Context(source, target, ids);
    try {
      passengers.detach();
      for (var mount : mounts) VehiclePhysics.forget(mount);
      for (var glue : glues.values()) glue.entity.discard();
      for (var part : parts) src.removeSubLevel(part.body, SubLevelRemovalReason.REMOVED);
      NativeTrainBridge.transferred(source, ids);
      links.move(true);
      for (var part : parts) load(target, part.destination);
      for (var glue : glues.values()) {
        var relocated = glue.data.copy(); TransferPlots.entity(relocated, moves.get(glue.owner));
        loadGlue(target, relocated);
      }
      for (var mount : mounts) {
        var loaded = target.getBlockEntity(TransferPlots.point(moves.values(), mount.getBlockPos()));
        if (!(loaded instanceof MountEntity plate)) throw new IllegalStateException("Missing transferred mount");
        VehiclePhysics.register(plate);
        if (VehiclePhysics.initialize(plate) == null) throw new IllegalStateException("Cannot reconnect transferred bogey");
      }
      var from = TrainIdentityData.get(source);
      var to = TrainIdentityData.get(target);
      for (var identity : identities) {
        from.ledger.records.remove(identity.id());
        to.ledger.records.put(identity.id(), identity);
      }
      from.setDirty(); to.setDirty();
      passengers.move(true);
      TransferRecoveryData.get(source.getServer()).moved(ids, target);
      TransferBackup.transition(backup, operation, "Committed");
      persist(source, target);
      TransferBackup.transition(backup, operation, "Transferred");
      TransferRecovery.unlock(source.getServer(), operation);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info("[PhysicalTransfer] completed operation={} bodies={} source={} target={} position={}",
              operation, parts.size(), source.dimension().location(), target.dimension().location(), destination);
      return new Result(operation, parts.size());
    } catch (RuntimeException failure) {
      boolean restored = true;
      TransferBackup.finish(backup, operation, "RollingBack");
      try { passengers.release(); }
      catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      try { links.move(false); }
      catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      for (var glue : glues.values()) {
        try {
          var copy = target.getEntity(glue.entity.getUUID());
          if (copy != null) copy.discard();
        } catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      }
      restored &= TransferRollback.restore(parts,
          part -> {
            var original = src.getSubLevel(part.body.getUniqueId());
            return original != null && !original.isRemoved();
          },
          part -> dst.getSubLevel(part.body.getUniqueId()) != null,
          part -> dst.removeSubLevel(dst.getSubLevel(part.body.getUniqueId()), SubLevelRemovalReason.REMOVED),
          part -> load(source, part.original), failure::addSuppressed);
      for (var glue : glues.values()) {
        try {
          var original = src.getSubLevel(glue.owner);
          if (target.getEntity(glue.entity.getUUID()) != null || original == null || original.isRemoved()) {
            restored = false; continue;
          }
          if (source.getEntity(glue.entity.getUUID()) == null) loadGlue(source, glue.data);
        } catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      }
      for (var mount : mounts) {
        try {
          if (source.getBlockEntity(mount.getBlockPos()) instanceof MountEntity restoredMount)
            VehiclePhysics.register(restoredMount);
        } catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      }
      for (var identity : identities) {
        TrainIdentityData.get(target).ledger.records.remove(identity.id());
        TrainIdentityData.get(source).ledger.records.put(identity.id(), identity);
      }
      TrainIdentityData.get(source).setDirty(); TrainIdentityData.get(target).setDirty();
      try { passengers.move(false); }
      catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      if (portal) try { park(source, ids); }
      catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      TransferRecoveryData.get(source.getServer()).moved(ids, source);
      if (restored) try { persist(source, target); }
      catch (RuntimeException undo) { failure.addSuppressed(undo); restored = false; }
      boolean recorded = TransferBackup.finish(backup, operation, restored ? "RolledBack" : "RecoveryRequired");
      if (restored && recorded) TransferRecovery.unlock(source.getServer(), operation);
      if (!recorded) restored = false;
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .error("[PhysicalTransfer] failed operation={} restored={} backup={}", operation, restored, backup, failure);
      throw reject(restored ? "failed" : "recovery");
    } finally {
      active = null;
      TrainConsists.invalidate(source); TrainConsists.invalidate(target);
      try {
        VehiclePhysics.refresh(source);
        PhysicalSignals.tick(source);
        PhysicalSignals.publish(source);
      } catch (RuntimeException cleanup) {
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] source signal refresh failed", cleanup);
      }
    }
  }

  static void load(ServerLevel level, CompoundTag data) {
    var result = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(data.copy()));
    if (result == null || result.isRemoved()) throw new IllegalStateException("Sable rejected transfer snapshot");
    result.updateLastPose(); result.updateBoundingBox();
    SubLevelPhysicsSystem.get(level).getPipeline().resetVelocity(result);
  }

  static net.minecraft.world.level.ChunkPos holdingChunk(CompoundTag snapshot) {
    var pos = SableNBTUtils.readPose3d(snapshot.getCompound("pose")).position();
    return new net.minecraft.world.level.ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z));
  }

  static void persist(ServerLevel source, ServerLevel target) {
    try {
      // In particular, an End arrival platform must reach disk before the journal is retired.
      target.getChunkSource().save(true);
      // Sable exposes a world-level save barrier, not a per-loaded-body commit API.
      for (var level : List.of(source, target)) {
        var map = SubLevelContainer.getContainer(level).getHoldingChunkMap();
        map.saveAll(); map.getStorage().flush();
        level.getDataStorage().save(); ((com.guidedrailways.mixin.ServerEntityStorageAccessor) level).guidedrailways$entityStorage().saveAll();
      }
      source.getServer().overworld().getDataStorage().save();
      source.getServer().getPlayerList().saveAll();
    } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot flush physical transfer", failure); }
  }

  static void recover(ServerLevel source, ServerLevel target, Set<UUID> ids, Runnable action) {
    if (active != null || !source.getServer().isSameThread() || SubLevelPhysicsSystem.currentlySteppingSystem != null) throw reject("busy");
    active = new Context(source, target, ids);
    try { action.run(); }
    finally { active = null; TrainConsists.invalidate(source); TrainConsists.invalidate(target); }
  }

  static void loadArea(ServerLevel level, AABB area, Set<net.minecraft.world.level.ChunkPos> chunks) {
    area = area.inflate(16, 0, 16); // Include nearby hosting chunks, not just the train's immediate block footprint.
    var map = SubLevelContainer.getContainer(level).getHoldingChunkMap();
    boolean changed = false;
    for (int x = net.minecraft.util.Mth.floor(area.minX) >> 4; x <= net.minecraft.util.Mth.floor(area.maxX) >> 4; x++)
      for (int z = net.minecraft.util.Mth.floor(area.minZ) >> 4; z <= net.minecraft.util.Mth.floor(area.maxZ) >> 4; z++) {
        var chunk = new net.minecraft.world.level.ChunkPos(x, z);
        if (chunks.add(chunk)) {
          if (chunks.size() > MAX_CHUNKS) throw reject("limit");
          level.getChunk(x, z);
          map.updateChunkStatus(chunk, true); changed = true;
        }
      }
    if (changed) map.processChanges();
  }

  static void loadGlue(ServerLevel level, CompoundTag data) {
    var glue = EntityType.loadEntityRecursive(data.copy(), level, java.util.function.Function.identity());
    if (!(glue instanceof SuperGlueEntity) || !level.addFreshEntity(glue))
      throw new IllegalStateException("Cannot restore transferred glue");
  }

  static AABB portalBounds(ServerSubLevel root) {
    AABB bounds = root.boundingBox().toMojang();
    for (var body : TransferGroup.collect(root.getLevel(), root).values()) bounds = bounds.minmax(body.boundingBox().toMojang());
    return bounds;
  }

  static void stopAtPortal(ServerLevel level, UUID id) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !(container.getSubLevel(id) instanceof ServerSubLevel root) || root.isRemoved()) return;
    Set<ServerSubLevel> bodies;
    try { bodies = new HashSet<>(TransferGroup.collect(level, root).values()); }
    catch (IllegalArgumentException incomplete) { bodies = new HashSet<>(CargoStorage.consist(level, root).bodies().values()); }
    for (var carrier : List.copyOf(bodies)) for (var entity : blockEntities(carrier))
      if (entity instanceof MountEntity mount && mount.bogieId != null
          && container.getSubLevel(mount.bogieId) instanceof ServerSubLevel bogie) bodies.add(bogie);
    park(level, bodies.stream().map(ServerSubLevel::getUniqueId).toList());
  }

  private static void park(ServerLevel level, Collection<UUID> ids) {
    var container = SubLevelContainer.getContainer(level);
    var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
    for (var id : ids) {
      if (!(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
      pipeline.resetVelocity(body);
      for (var entity : blockEntities(body)) {
        if (entity instanceof DriverConsoleEntity console) {
          console.schedule.pause(ScheduleController.PAUSED);
          console.emergency = true; console.notch = -4;
          console.setChanged(); console.updateVisual();
        }
      }
    }
  }

  private static List<BlockEntity> blockEntities(ServerSubLevel body) {
    return TransferGroup.entities(body);
  }

  private static void validateBlockEntity(ServerLevel level, BlockEntity be, boolean portal) {
    if (be instanceof MountEntity || be instanceof BogieEntity) return;
    if (be instanceof DriverConsoleEntity console) {
      if (!portal && (console.active || console.operator != null || console.schedule.running()
          || console.notch >= 0 && !console.emergency))
        throw reject("controls");
      return;
    }
    var coupler = CouplerEndpoint.get(be);
    if (coupler != null) {
      if (coupler.handoff != null)
        throw reject("coupled");
      return;
    }
    if (be instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity
        || be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) {
      var multi = (com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer) be;
      var controller = multi.getController();
      var helper = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE;
      if (controller == null || !level.hasChunkAt(controller) || multi.getControllerBE() == null
          || helper.getContaining(level, controller) != helper.getContaining(level, be.getBlockPos())
          || multi.getLastKnownPos() != null && !multi.getLastKnownPos().equals(be.getBlockPos())) throw reject("incomplete");
      return;
    }
    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
    if (id.equals("create:kinetic") || id.equals("create:creative_motor") || id.equals("create:gearbox")) return;
    // Basic local storage has no cross-world address or external network to migrate.
    if (be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity
        && id.startsWith("minecraft:") && !id.equals("minecraft:ender_chest")) return;
    throw reject("unsupported");
  }

  private static void resetKinetics(CompoundTag data) {
    var chunks = data.getCompound("plot").getCompound("chunks");
    for (String key : chunks.getAllKeys())
      for (var value : chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND)) {
        var be = (CompoundTag) value;
        String id = be.getString("id");
        if (id.startsWith("create:") || id.equals("guidedrailways:bogie_mount")) {
          be.remove("Network"); be.remove("Source");
        }
      }
  }

}
