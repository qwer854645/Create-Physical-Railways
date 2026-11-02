package com.guidedrailways;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Pre-mutation evidence; a completed portal snapshot may be atomically replaced for the same body. */
final class TransferBackup {
  static final int MAX_FILES = 32, MAX_NBT_BYTES = 16 * 1024 * 1024;
  private TransferBackup() {}

  static Path prepare(ServerLevel source, ServerLevel target, UUID operation,
      List<CompoundTag> originals, List<CompoundTag> destinations, List<CompoundTag> glue,
      List<CompoundTag> links, List<TrainIdentityLedger.Identity> identities, List<CompoundTag> passengers,
      List<CompoundTag> hints, UUID retainedKey) {
    var record = new CompoundTag();
    record.putInt("Version", 3);
    record.putUUID("Operation", operation);
    if (retainedKey != null) record.putUUID("PortalBody", retainedKey);
    record.putString("Source", source.dimension().location().toString());
    record.putString("Target", target.dimension().location().toString());
    var before = new ListTag(); originals.forEach(before::add);
    var after = new ListTag(); destinations.forEach(after::add);
    record.put("Originals", before); record.put("Destinations", after);
    var entities = new ListTag(); glue.forEach(entities::add); record.put("Glue", entities);
    var pairs = new ListTag(); links.forEach(pairs::add); record.put("Links", pairs);
    var trains = new ListTag();
    for (var identity : identities) {
      var tag = new CompoundTag(); tag.putUUID("Id", identity.id()); tag.putUUID("Anchor", identity.anchor());
      var members = new ListTag();
      for (var id : identity.members()) { var member = new CompoundTag(); member.putUUID("Id", id); members.add(member); }
      tag.put("Members", members); trains.add(tag);
    }
    record.put("Trains", trains);
    var riders = new ListTag(); passengers.forEach(riders::add); record.put("Passengers", riders);
    var holding = new ListTag(); hints.forEach(holding::add); record.put("HoldingHints", holding);
    if (record.sizeInBytes() > MAX_NBT_BYTES) throw new IllegalArgumentException("message.guidedrailways.transfer.limit");
    Path directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("guidedrailways-transfer-backups");
    Path file = directory.resolve((retainedKey == null ? operation : retainedKey) + ".nbt");
    Path temporary = directory.resolve(operation + ".tmp");
    try {
      Files.createDirectories(directory);
      if (Files.exists(file)) {
        var previous = TransferJournal.read(file);
        if (!TransferJournal.terminal(TransferJournal.stage(file, previous.getUUID("Operation"))))
          throw new IllegalArgumentException("message.guidedrailways.transfer.recovery");
      }
      try (var files = Files.list(directory)) {
        if (!Files.exists(file)
            && files.filter(path -> path.getFileName().toString().endsWith(".nbt")).limit(MAX_FILES).count() >= MAX_FILES)
          throw new IllegalArgumentException("message.guidedrailways.transfer.backups_full");
      }
      NbtIo.writeCompressed(record, temporary);
      TransferJournal.force(temporary);
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      TransferJournal.decision(file, operation, "Prepared");
      return file;
    } catch (IOException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] cannot write backup {}", file, failure);
      throw new IllegalArgumentException("message.guidedrailways.transfer.backup_failed", failure);
    } finally {
      try { Files.deleteIfExists(temporary); }
      catch (IOException ignored) { /* No source mutation has happened; the next operation uses a fresh UUID. */ }
    }
  }

  static void transition(Path file, UUID operation, String status) {
    try { TransferJournal.decision(file, operation, status); }
    catch (IOException failure) { throw new IllegalStateException("Cannot persist transfer decision " + status, failure); }
  }

  static boolean finish(Path file, UUID operation, String status) {
    try { TransferJournal.decision(file, operation, status); return true; }
    catch (IOException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[PhysicalTransfer] status {} unavailable for {}", status, file, failure);
      return false;
    }
  }
}
