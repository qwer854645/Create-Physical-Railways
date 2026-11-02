package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.util.SableNBTUtils;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Offline players retain their own inventory data; only a recovery position is applied on login. */
final class TransferRecoveryData extends SavedData {
  static final int MAX_ENTRIES = TransferBackup.MAX_FILES * TransferPassengers.MAX_ENTITIES;
  private final Map<UUID, CompoundTag> pending = new HashMap<>();
  static TransferRecoveryData get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(TransferRecoveryData::new, TransferRecoveryData::load, null), "guidedrailways_transfer_players");
  }
  static TransferRecoveryData load(CompoundTag root, HolderLookup.Provider registries) {
    var data = new TransferRecoveryData();
    for (var value : root.getList("Players", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value;
      if (tag.hasUUID("Id") && data.pending.size() < MAX_ENTRIES)
        data.pending.put(tag.getUUID("Id"), tag.copy());
    }
    return data;
  }
  @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
    var list = new ListTag(); pending.values().forEach(tag -> list.add(tag.copy())); root.put("Players", list); return root;
  }

  void remember(ServerLevel level, CompoundTag snapshot, CompoundTag entry, boolean forward) {
    if (!pending.containsKey(entry.getUUID("Id")) && pending.size() >= MAX_ENTRIES)
      throw new IllegalArgumentException("message.guidedrailways.transfer.recovery");
    var tag = new CompoundTag(); tag.putUUID("Id", entry.getUUID("Id")); tag.putUUID("Body", entry.getUUID("Body"));
    tag.putString("Dimension", level.dimension().location().toString());
    if (entry.hasUUID("Vehicle")) tag.putUUID("Vehicle", entry.getUUID("Vehicle"));
    var pose = SableNBTUtils.readPose3d(snapshot.getCompound("pose"));
    var position = vector(entry, forward ? "After" : "Before");
    var body = SubLevelContainer.getContainer(level).getSubLevel(entry.getUUID("Body"));
    if (body == null || body.isRemoved()) throw new IllegalArgumentException("message.guidedrailways.transfer.recovery");
    // Plot center is stable when cargo changes the center of mass, and follows plot relocation.
    var local = pose.transformPositionInverse(position).subtract(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    tag.put("Local", vector(local)); tag.putLong("Chunk", PhysicalTransfer.holdingChunk(snapshot).toLong());
    tag.putFloat("Yaw", entry.getFloat("Yaw") - (forward ? entry.getInt("Turns") * 90 : 0)); tag.putFloat("Pitch", entry.getFloat("Pitch"));
    pending.put(entry.getUUID("Id"), tag); setDirty();
  }

  void login(ServerPlayer player) {
    var ticket = pending.get(player.getUUID()); if (ticket == null) return;
    var server = player.getServer();
    ServerSubLevel body = null;
    for (var world : server.getAllLevels()) {
      var container = SubLevelContainer.getContainer(world);
      if (container != null && container.getSubLevel(ticket.getUUID("Body")) instanceof ServerSubLevel found && !found.isRemoved()) { body = found; break; }
    }
    var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ticket.getString("Dimension")));
    var target = body != null ? body.getLevel() : server.getLevel(dimension);
    if (target == null) return;
    if (body == null) {
      var chunk = new net.minecraft.world.level.ChunkPos(ticket.getLong("Chunk")); target.getChunk(chunk.x, chunk.z);
      var container = SubLevelContainer.getContainer(target);
      if (container != null) {
        var map = container.getHoldingChunkMap(); map.updateChunkStatus(chunk, true); map.processChanges();
        var holding = map.getHoldingSubLevel(ticket.getUUID("Body")); if (holding != null) map.loadHoldingSubLevel(holding);
        if (container.getSubLevel(ticket.getUUID("Body")) instanceof ServerSubLevel found && !found.isRemoved()) body = found;
      }
    }
    Vec3 position;
    if (body != null) {
      target = body.getLevel();
      position = body.logicalPose().transformPosition(vector(ticket, "Local").add(Vec3.atCenterOf(body.getPlot().getCenterBlock())));
    } else {
      var spawn = target.getSharedSpawnPos(); target.getChunkAt(spawn);
      position = new Vec3(spawn.getX() + .5, target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()) + 1, spawn.getZ() + .5);
    }
    player.stopRiding(); TransferPassengers.clear(player);
    if (!player.teleportTo(target, position.x, position.y, position.z, Set.of(), ticket.getFloat("Yaw"), ticket.getFloat("Pitch"))) return;
    player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0; player.setPortalCooldown();
    if (body != null) {
      ((EntityMovementExtension) player).sable$setTrackingSubLevel(body);
      ((EntityStickExtension) player).sable$setPlotPosition(body.logicalPose().transformPositionInverse(position));
      if (ticket.hasUUID("Vehicle")) {
        var vehicle = target.getEntity(ticket.getUUID("Vehicle"));
        if (vehicle != null) player.startRiding(vehicle, true);
      }
    }
    server.getPlayerList().saveAll();
    pending.remove(player.getUUID()); setDirty(); server.overworld().getDataStorage().save();
  }

  void moved(Set<UUID> ids, ServerLevel target) {
    for (var tag : pending.values()) if (ids.contains(tag.getUUID("Body"))) {
      var body = SubLevelContainer.getContainer(target).getSubLevel(tag.getUUID("Body"));
      if (body == null || body.isRemoved()) continue;
      tag.putString("Dimension", target.dimension().location().toString());
      var pos = body.logicalPose().position(); tag.putLong("Chunk", new net.minecraft.world.level.ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z)).toLong()); setDirty();
    }
  }

  static Vec3 vector(CompoundTag tag, String key) {
    var list = tag.getList(key, Tag.TAG_DOUBLE);
    if (list.size() != 3 || !Double.isFinite(list.getDouble(0)) || !Double.isFinite(list.getDouble(1)) || !Double.isFinite(list.getDouble(2)))
      throw new IllegalArgumentException("message.guidedrailways.transfer.recovery");
    return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
  }
  private static ListTag vector(Vec3 point) {
    var list = new ListTag(); list.add(DoubleTag.valueOf(point.x)); list.add(DoubleTag.valueOf(point.y)); list.add(DoubleTag.valueOf(point.z)); return list;
  }
}
