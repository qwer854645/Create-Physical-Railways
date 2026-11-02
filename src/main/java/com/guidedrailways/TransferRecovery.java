package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.LevelResource;

/** Replays only unfinished, operation-bound journals. Completed snapshots are never replayed. */
final class TransferRecovery {
  static final int MAX_ENTRIES = TransferBackup.MAX_FILES * PhysicalTransfer.MAX_BODIES;
  private static final Map<MinecraftServer, Map<UUID, UUID>> LOCKS = new WeakHashMap<>();
  static void clear(MinecraftServer server) { LOCKS.remove(server); }
  static void check(MinecraftServer server, Set<UUID> ids) {
    var locks = LOCKS.get(server);
    if (locks != null && ids.stream().anyMatch(locks::containsKey)) throw error();
  }
  static void lock(MinecraftServer server, Set<UUID> ids, UUID operation) {
    var locks = LOCKS.computeIfAbsent(server, ignored -> new HashMap<>());
    if ((long) locks.size() + ids.stream().filter(id -> !locks.containsKey(id)).count() > MAX_ENTRIES) throw error();
    ids.forEach(id -> locks.put(id, operation));
  }
  static void unlock(MinecraftServer server, UUID operation) {
    var locks = LOCKS.get(server);
    if (locks != null) { locks.values().removeIf(operation::equals); if (locks.isEmpty()) LOCKS.remove(server); }
  }

  static Path directory(MinecraftServer server) {
    return server.getWorldPath(LevelResource.ROOT).resolve("guidedrailways-transfer-backups");
  }

  static List<Path> files(MinecraftServer server) throws java.io.IOException {
    var directory = directory(server);
    if (!Files.isDirectory(directory)) return List.of();
    try (var files = Files.list(directory)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".nbt"))
          .sorted(Comparator.comparingLong(TransferRecovery::modified).thenComparing(Path::toString))
          .limit(TransferBackup.MAX_FILES + 1L).toList();
    }
  }

  static void startup(MinecraftServer server) {
    try {
      var files = files(server);
      if (files.size() > TransferBackup.MAX_FILES) throw new IllegalStateException("Too many transfer journals");
      for (var file : files) try {
        var record = TransferJournal.read(file); var operation = record.getUUID("Operation");
        var ids = ids(record.getList("Originals", Tag.TAG_COMPOUND));
        lock(server, ids, operation);
        String stage = TransferJournal.stage(file, operation);
        if (TransferJournal.terminal(stage)) { unlock(server, operation); continue; }
        replay(server, file, record, TransferJournal.destination(stage));
      } catch (Exception failure) {
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] recovery required: {}", file, failure);
      }
    } catch (Exception failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] cannot inspect journals", failure);
    }
  }

  private static long modified(Path path) {
    try { return Files.getLastModifiedTime(path).toMillis(); }
    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
  }

  static void login(ServerPlayer player) {
    try { TransferRecoveryData.get(player.getServer()).login(player); }
    catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] player recovery pending {}", player.getUUID(), failure);
    }
  }

  static int retry(MinecraftServer server, UUID operation) {
    try {
      for (var file : files(server)) {
        var record = TransferJournal.read(file);
        if (!operation.equals(record.getUUID("Operation"))) continue;
        var stage = TransferJournal.stage(file, operation);
        if (TransferJournal.terminal(stage)) throw error();
        return replay(server, file, record, TransferJournal.destination(stage));
      }
    } catch (java.io.IOException failure) { throw new IllegalArgumentException("message.guidedrailways.transfer.recovery", failure); }
    throw error();
  }

  private static Set<UUID> ids(ListTag snapshots) {
    if (snapshots.isEmpty() || snapshots.size() > PhysicalTransfer.MAX_BODIES) throw error();
    var result = new HashSet<UUID>();
    for (var value : snapshots) {
      var tag = (CompoundTag) value;
      if (!tag.hasUUID("uuid") || !result.add(tag.getUUID("uuid"))) throw error();
    }
    return Set.copyOf(result);
  }

  private static ServerLevel level(MinecraftServer server, String id) {
    var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)));
    if (level == null || SubLevelContainer.getContainer(level) == null) throw error();
    return level;
  }

  private static int replay(MinecraftServer server, Path file, CompoundTag record, boolean forward) {
    var source = level(server, record.getString("Source")); var target = level(server, record.getString("Target"));
    if (source == target) throw error();
    var originals = record.getList("Originals", Tag.TAG_COMPOUND); var destinations = record.getList("Destinations", Tag.TAG_COMPOUND);
    var ids = ids(originals);
    if (!ids.equals(ids(destinations)) || record.getList("Passengers", Tag.TAG_COMPOUND).size() > TransferPassengers.MAX_ENTITIES
        || record.getList("Glue", Tag.TAG_COMPOUND).size() > PhysicalTransfer.MAX_GLUE) throw error();
    UUID operation = record.getUUID("Operation"); lock(server, ids, operation);
    var chosen = forward ? target : source; var snapshots = forward ? destinations : originals;
    PhysicalTransfer.recover(source, target, ids, () -> {
      // Load known old hosting chunks as well as current positions; a train may have moved since its last native save.
      for (var value : record.getList("HoldingHints", Tag.TAG_COMPOUND)) {
        var hint = (CompoundTag) value;
        if (hint.contains("SavedSource", Tag.TAG_LONG)) warm(source, hint.getLong("SavedSource"));
        warm(source, hint.getLong("Source")); warm(target, hint.getLong("Target"));
      }
      for (var world : List.of(source, target)) {
        var container = SubLevelContainer.getContainer(world);
        for (var id : ids) {
          var holding = container.getHoldingChunkMap().getHoldingSubLevel(id);
          if (holding != null) container.getHoldingChunkMap().loadHoldingSubLevel(holding);
        }
      }
      var entities = new ArrayList<CompoundTag>();
      for (var value : record.getList("Passengers", Tag.TAG_COMPOUND)) entities.add((CompoundTag) value);
      for (var value : record.getList("Glue", Tag.TAG_COMPOUND)) {
        var glue = (CompoundTag) value; var data = glue.getCompound("Data");
        var entry = new CompoundTag(); entry.putUUID("Id", data.getUUID("UUID")); entry.putUUID("Body", glue.getUUID("Owner"));
        entry.put("Data", data); entry.put("Before", data.getList("Pos", Tag.TAG_DOUBLE));
        entry.put("After", glue.getCompound("NextData").getList("Pos", Tag.TAG_DOUBLE)); entry.putBoolean("Plot", true);
        entities.add(entry);
      }
      // Entity section data is separate from the Sable snapshots. Finish pending reads before UUID checks.
      for (var entity : entities) if (!entity.getBoolean("Player")) {
        warmEntity(source, TransferRecoveryData.vector(entity, "Before"));
        warmEntity(target, TransferRecoveryData.vector(entity, "After"));
      }
      for (var world : List.of(source, target)) ((com.guidedrailways.mixin.ServerEntityStorageAccessor) world).guidedrailways$entityStorage().saveAll();
      try {
      validateMetadata(source, target, record);
      for (var value : snapshots) {
        var snapshot = (CompoundTag) value; var plot = snapshot.getCompound("plot");
        var container = SubLevelContainer.getContainer(chosen);
        var occupant = container.getSubLevel(plot.getInt("plot_x"), plot.getInt("plot_z"));
        if (occupant != null && !ids.contains(occupant.getUniqueId())) throw error();
        // Occupancy without a loaded owner may belong to an unrelated dormant body. Never overwrite it.
        if (occupant == null && container.getOccupancy().get(container.getIndex(plot.getInt("plot_x"), plot.getInt("plot_z")))) throw error();
      }
      // Check entity UUID/type collisions before any deletion.
      for (var entry : entities) for (var world : List.of(source, target)) {
        var entity = world.getEntity(entry.getUUID("Id"));
        if (entity == null) continue;
        if ((entity instanceof ServerPlayer) != entry.getBoolean("Player")
            || !entry.getBoolean("Player") && !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                .toString().equals(entry.getCompound("Data").getString("id"))) throw error();
      }
      // Preserve live players; other entity UUIDs are reconstructed once from the recorded state.
      for (var entry : entities) for (var world : List.of(source, target)) {
        var entity = world.getEntity(entry.getUUID("Id"));
        if (entity == null) continue;
        entity.stopRiding(); TransferPassengers.clear(entity);
        if (entity instanceof ServerPlayer) {
          var body = SubLevelContainer.getContainer(world).getSubLevel(entry.getUUID("Body"));
          if (body != null && dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(world, entity.position()) == body)
            entity.moveTo(body.logicalPose().transformPosition(entity.position()));
        } else entity.discard();
      }
      for (var world : List.of(source, target)) {
        var container = SubLevelContainer.getContainer(world);
        for (var id : ids) {
          var body = container.getSubLevel(id);
          if (body != null) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        NativeTrainBridge.transferred(world, ids);
      }
      metadata(source, target, record, forward);
      for (var value : snapshots) {
        var snapshot = ((CompoundTag) value).copy(); TransferControls.park(snapshot);
        PhysicalTransfer.load(chosen, snapshot);
      }
      for (var entry : entities) if (!entry.getBoolean("Player")) {
        var data = entry.getCompound("Data").copy(); data.remove("Passengers");
        data.put("Pos", entry.getList(forward ? "After" : "Before", Tag.TAG_DOUBLE).copy());
        var entity = EntityType.loadEntityRecursive(data, chosen, java.util.function.Function.identity());
        var body = SubLevelContainer.getContainer(chosen).getSubLevel(entry.getUUID("Body"));
        if (entity == null || body == null || body.isRemoved()) throw error();
        if (!entry.getBoolean("Plot")) {
          entity.setYRot(entry.getFloat("Yaw") - (forward ? entry.getInt("Turns") * 90 : 0));
          entity.setXRot(entry.getFloat("Pitch")); entity.setYHeadRot(entity.getYRot());
        }
        TransferPassengers.attach(entity, body, entry.getBoolean("Plot"), entity.position());
        if (!chosen.addFreshEntity(entity)) throw error();
      }
      for (var entry : entities) if (!entry.getBoolean("Player") && entry.hasUUID("Vehicle")) {
        var entity = chosen.getEntity(entry.getUUID("Id")); var vehicle = chosen.getEntity(entry.getUUID("Vehicle"));
        if (entity == null || vehicle == null || !entity.startRiding(vehicle, true)) throw error();
      }
      var tickets = TransferRecoveryData.get(server);
      for (var entry : entities) if (entry.getBoolean("Player")) {
        var snapshot = (CompoundTag) snapshots.stream().filter(value -> ((CompoundTag) value).getUUID("uuid").equals(entry.getUUID("Body"))).findFirst().orElseThrow();
        tickets.remember(chosen, snapshot, entry, forward);
      }
      // Online command recovery uses the same inventory-preserving login path as restart recovery.
      for (var player : List.copyOf(server.getPlayerList().getPlayers())) tickets.login(player);
      PhysicalTransfer.persist(source, target);
      TransferBackup.transition(file, operation, forward ? "Transferred" : "RolledBack");
      unlock(server, operation);
      } finally {
        for (var entry : entities) if (!entry.getBoolean("Player")) {
          releaseEntityChunk(source, TransferRecoveryData.vector(entry, "Before"));
          releaseEntityChunk(target, TransferRecoveryData.vector(entry, "After"));
        }
      }
    });
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[PhysicalTransfer] recovered operation={} dimension={} bodies={}", operation, chosen.dimension().location(), ids.size());
    return ids.size();
  }

  private static void warm(ServerLevel level, long chunkKey) {
    var chunk = new ChunkPos(chunkKey); level.getChunk(chunk.x, chunk.z);
    var map = SubLevelContainer.getContainer(level).getHoldingChunkMap(); map.updateChunkStatus(chunk, true); map.processChanges();
  }

  private static void warmEntity(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
    ((com.guidedrailways.mixin.ServerEntityStorageAccessor) level).guidedrailways$entityStorage()
        .updateChunkStatus(new ChunkPos(BlockPos.containing(pos)), Visibility.TRACKED);
  }

  private static void releaseEntityChunk(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
    var chunk = new ChunkPos(BlockPos.containing(pos));
    var container = SubLevelContainer.getContainer(level);
    boolean orphanedPlot = container.inBounds(chunk)
        && dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, chunk) == null;
    if (orphanedPlot || level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null)
      ((com.guidedrailways.mixin.ServerEntityStorageAccessor) level).guidedrailways$entityStorage().updateChunkStatus(chunk, Visibility.HIDDEN);
  }

  private static void validateMetadata(ServerLevel source, ServerLevel target, CompoundTag record) {
    for (var value : record.getList("Links", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value; var expected = new CouplingLinks.Link(tag.getUUID("Id"), tag.getUUID("A"), tag.getUUID("B"));
      for (var world : List.of(source, target)) {
        var data = CouplerSavedData.get(world); var existing = data.links.get(expected.id());
        if (existing != null && !existing.equals(expected)) throw error();
        if (!data.owns(expected.a(), BlockPos.of(tag.getLong(world == source ? "AOld" : "ANew")))
            || !data.owns(expected.b(), BlockPos.of(tag.getLong(world == source ? "BOld" : "BNew")))) throw error();
        for (var endpoint : List.of(expected.a(), expected.b())) {
          var other = data.links.forEndpoint(endpoint);
          if (other != null && !other.equals(expected)) throw error();
        }
      }
    }
    var expected = TrainIdentityData.load(record, source.registryAccess());
    for (var entry : expected.ledger.records.entrySet()) for (var world : List.of(source, target)) {
      var existing = TrainIdentityData.get(world).ledger.records.get(entry.getKey());
      if (existing != null && !existing.equals(entry.getValue())) throw error();
    }
  }

  private static void metadata(ServerLevel source, ServerLevel target, CompoundTag record, boolean forward) {
    for (var value : record.getList("Links", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value; var link = new CouplingLinks.Link(tag.getUUID("Id"), tag.getUUID("A"), tag.getUUID("B"));
      for (var world : List.of(source, target)) {
        CouplerConnections.suspend(world, link.a()); CouplerConnections.suspend(world, link.b());
        CouplerSavedData.get(world).remove(link.id());
      }
      CouplerSavedData.get(forward ? target : source).add(link,
          BlockPos.of(tag.getLong(forward ? "ANew" : "AOld")), BlockPos.of(tag.getLong(forward ? "BNew" : "BOld")));
    }
    var identities = TrainIdentityData.load(record, source.registryAccess());
    for (var identity : identities.ledger.records.values()) {
      TrainIdentityData.get(source).ledger.records.remove(identity.id()); TrainIdentityData.get(target).ledger.records.remove(identity.id());
      TrainIdentityData.get(forward ? target : source).ledger.records.put(identity.id(), identity);
    }
    TrainIdentityData.get(source).setDirty(); TrainIdentityData.get(target).setDirty();
  }

  private static IllegalArgumentException error() { return new IllegalArgumentException("message.guidedrailways.transfer.recovery"); }
  private TransferRecovery() {}
}
