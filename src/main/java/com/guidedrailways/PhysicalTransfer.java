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

/** Server-thread, stopped, unoccupied carriage transfer. No third-party teleport implementation. */
public final class PhysicalTransfer {
  static final int MAX_BODIES = 16, MAX_BLOCKS = 32768, MAX_VOLUME = 65536, MAX_CHUNKS = 16, MAX_GLUE = 256;
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
    var members = new LinkedHashMap<UUID, ServerSubLevel>();
    members.put(root.getUniqueId(), root);
    var mounts = new ArrayList<MountEntity>();
    for (var be : blockEntities(root)) {
      if (be instanceof MountEntity mount) {
        if (mount.bogieId == null || mount.handoff != null
            || !(src.getSubLevel(mount.bogieId) instanceof ServerSubLevel lower) || lower.isRemoved()
            || lower == root || !BogieMountBinding.matches(lower.getUserDataTag(),
                VehiclePhysics.mountLocation(source, mount.getBlockPos()))) throw reject("incomplete");
        if (members.putIfAbsent(lower.getUniqueId(), lower) != null) throw reject("incomplete");
        mounts.add(mount);
      }
    }
    if (members.size() > MAX_BODIES) throw reject("limit");
    var ids = Set.copyOf(members.keySet());
    var identities = TrainIdentityData.get(source).ledger.records.values().stream()
        .filter(identity -> identity.members().stream().anyMatch(ids::contains)).toList();
    var targetIdentities = TrainIdentityData.get(target).ledger.records;
    for (var identity : identities)
      if (!ids.containsAll(identity.members()) || targetIdentities.containsKey(identity.id())
          || targetIdentities.values().stream().anyMatch(other -> other.members().stream().anyMatch(ids::contains)))
        throw reject("incomplete");
    var anchor = root.logicalPose().position();
    var transform = new PortalTransform(new Vec3(anchor.x, anchor.y, anchor.z), destination, turns);
    var parts = new ArrayList<Part>();
    var glues = new LinkedHashMap<UUID, Glue>();
    var loadedChunks = new HashSet<net.minecraft.world.level.ChunkPos>();
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
      for (int x = net.minecraft.util.Mth.floor(arrival.minX) >> 4;
          x <= net.minecraft.util.Mth.floor(arrival.maxX) >> 4; x++)
        for (int z = net.minecraft.util.Mth.floor(arrival.minZ) >> 4;
            z <= net.minecraft.util.Mth.floor(arrival.maxZ) >> 4; z++) {
          var chunk = new net.minecraft.world.level.ChunkPos(x, z);
          if (loadedChunks.add(chunk)) {
            if (loadedChunks.size() > MAX_CHUNKS) throw reject("limit");
            target.getChunk(x, z); // Collision queries alone can treat unloaded chunks as empty.
          }
        }
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
      int x = plot.getInt("plot_x"), z = plot.getInt("plot_z");
      int side = 1 << dst.getLogSideLength();
      if (x < 0 || z < 0 || x >= side || z >= side || dst.getOccupancy().get(dst.getIndex(x, z))
          || dst.getSubLevel(body.getUniqueId()) != null) throw reject("occupied");
      TransferSections.shift(plot, source.getMinSection() - target.getMinSection(), target.getSectionsCount());
      var pose = SableNBTUtils.readPose3d(copy.getCompound("pose"));
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
        TransferControls.park(copy);
      }
      if (source.getBlockState(body.getPlot().getCenterBlock()).is(Content.BOGIE.get())) {
        var data = copy.getCompound("user_data");
        var owner = BogieMountBinding.owner(data);
        if (owner == null) throw reject("incomplete");
        var next = owner.moveTo(owner.location(), new MountOwnership.Location(
            target.dimension().location().toString(), owner.location().position()), owner.revision());
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
    UUID operation = UUID.randomUUID();
    var backup = TransferBackup.prepare(source, target, operation,
        parts.stream().map(Part::original).toList(), parts.stream().map(Part::destination).toList(),
        glues.values().stream().map(glue -> {
          var tag = new CompoundTag(); tag.putUUID("Owner", glue.owner); tag.put("Data", glue.data); return tag;
        }).toList(), portal ? root.getUniqueId() : null);
    active = new Context(source, target, ids);
    try {
      for (var mount : mounts) VehiclePhysics.forget(mount);
      for (var glue : glues.values()) glue.entity.discard();
      for (var part : parts) src.removeSubLevel(part.body, SubLevelRemovalReason.REMOVED);
      NativeTrainBridge.transferred(source, ids);
      for (var part : parts) load(target, part.destination);
      for (var glue : glues.values()) loadGlue(target, glue.data);
      for (var mount : mounts) {
        var loaded = target.getBlockEntity(mount.getBlockPos());
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
      TransferBackup.finish(backup, "Transferred");
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info("[PhysicalTransfer] completed operation={} bodies={} source={} target={} position={}",
              operation, parts.size(), source.dimension().location(), target.dimension().location(), destination);
      return new Result(operation, parts.size());
    } catch (RuntimeException failure) {
      boolean restored = true;
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
      TransferBackup.finish(backup, restored ? "RolledBack" : "RecoveryRequired");
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

  private static void load(ServerLevel level, CompoundTag data) {
    var result = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(data.copy()));
    if (result == null || result.isRemoved()) throw new IllegalStateException("Sable rejected transfer snapshot");
    result.updateLastPose(); result.updateBoundingBox();
    SubLevelPhysicsSystem.get(level).getPipeline().resetVelocity(result);
  }

  private static void loadGlue(ServerLevel level, CompoundTag data) {
    var glue = EntityType.loadEntityRecursive(data.copy(), level, java.util.function.Function.identity());
    if (!(glue instanceof SuperGlueEntity) || !level.addFreshEntity(glue))
      throw new IllegalStateException("Cannot restore transferred glue");
  }

  static AABB portalBounds(ServerSubLevel root) {
    AABB bounds = root.boundingBox().toMojang();
    var container = SubLevelContainer.getContainer(root.getLevel());
    for (var entity : blockEntities(root)) if (entity instanceof MountEntity mount) {
      if (mount.bogieId == null || !(container.getSubLevel(mount.bogieId) instanceof ServerSubLevel bogie)
          || bogie.isRemoved()) throw reject("incomplete");
      bounds = bounds.minmax(bogie.boundingBox().toMojang());
    }
    return bounds;
  }

  static void stopAtPortal(ServerLevel level, UUID id) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !(container.getSubLevel(id) instanceof ServerSubLevel root) || root.isRemoved()) return;
    var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
    pipeline.resetVelocity(root);
    for (var entity : blockEntities(root)) {
      if (entity instanceof MountEntity mount && mount.bogieId != null
          && container.getSubLevel(mount.bogieId) instanceof ServerSubLevel bogie) pipeline.resetVelocity(bogie);
      if (entity instanceof DriverConsoleEntity console) {
        console.schedule.pause(ScheduleController.PAUSED);
        console.emergency = true; console.notch = -4;
        console.setChanged(); console.updateVisual();
      }
    }
  }

  private static List<BlockEntity> blockEntities(ServerSubLevel body) {
    var result = new ArrayList<BlockEntity>();
    for (var holder : body.getPlot().getLoadedChunks()) result.addAll(holder.getChunk().getBlockEntities().values());
    return result;
  }

  private static void validateBlockEntity(ServerLevel level, BlockEntity be, boolean portal) {
    if (be instanceof MountEntity || be instanceof BogieEntity) return;
    if (be instanceof DriverConsoleEntity console) {
      if (!portal && (console.active || console.operator != null || console.schedule.running()
          || console.notch >= 0 && !console.emergency))
        throw reject("controls");
      return;
    }
    if (be instanceof CouplerEntity coupler) {
      if (coupler.endpoint.handoff != null
          || CouplerSavedData.get(level).links.forEndpoint(coupler.endpoint.endpointId) != null)
        throw reject("coupled");
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
