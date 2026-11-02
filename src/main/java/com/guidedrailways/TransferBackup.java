package com.guidedrailways;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Immutable pre-mutation evidence. Deliberately no automatic restore over a newer world. */
final class TransferBackup {
  static final int MAX_FILES = 32, MAX_NBT_BYTES = 16 * 1024 * 1024;
  private TransferBackup() {}

  static Path prepare(ServerLevel source, ServerLevel target, UUID operation,
      List<CompoundTag> originals, List<CompoundTag> destinations, List<CompoundTag> glue) {
    var record = new CompoundTag();
    record.putInt("Version", 1);
    record.putUUID("Operation", operation);
    record.putString("Source", source.dimension().location().toString());
    record.putString("Target", target.dimension().location().toString());
    var before = new ListTag(); originals.forEach(before::add);
    var after = new ListTag(); destinations.forEach(after::add);
    record.put("Originals", before); record.put("Destinations", after);
    var entities = new ListTag(); glue.forEach(entities::add); record.put("Glue", entities);
    if (record.sizeInBytes() > MAX_NBT_BYTES) throw new IllegalArgumentException("message.guidedrailways.transfer.limit");
    Path directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("guidedrailways-transfer-backups");
    Path file = directory.resolve(operation + ".nbt");
    Path temporary = directory.resolve(operation + ".tmp");
    try {
      Files.createDirectories(directory);
      try (var files = Files.list(directory)) {
        if (files.filter(path -> path.getFileName().toString().endsWith(".nbt")).limit(MAX_FILES).count() >= MAX_FILES)
          throw new IllegalArgumentException("message.guidedrailways.transfer.backups_full");
      }
      NbtIo.writeCompressed(record, temporary);
      try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
      return file;
    } catch (IOException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[PhysicalTransfer] cannot write backup {}", file, failure);
      throw new IllegalArgumentException("message.guidedrailways.transfer.backup_failed", failure);
    } finally {
      try { Files.deleteIfExists(temporary); }
      catch (IOException ignored) { /* No source mutation has happened; the next operation uses a fresh UUID. */ }
    }
  }

  static void finish(Path file, String status) {
    try { Files.writeString(file.resolveSibling(file.getFileName() + ".status"), status + System.lineSeparator()); }
    catch (IOException failure) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[PhysicalTransfer] status {} unavailable for {}", status, file, failure);
    }
  }
}
