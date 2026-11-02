package com.guidedrailways;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.nbt.*;

/** Small atomic decision record bound to an immutable snapshot's operation UUID. */
final class TransferJournal {
  static Path statusFile(Path snapshot) { return snapshot.resolveSibling(snapshot.getFileName() + ".status"); }

  static String stage(Path snapshot, UUID operation) throws IOException {
    var path = statusFile(snapshot);
    if (!Files.exists(path)) return "Prepared";
    if (Files.size(path) > 256) throw new IOException("Oversized transfer decision");
    var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    // A crash after replacing the snapshot but before replacing its decision must not reuse
    // the preceding operation's commit. The new operation has not mutated the world yet.
    if (lines.size() != 2) throw new IOException("Malformed transfer decision");
    try { UUID.fromString(lines.getFirst()); }
    catch (IllegalArgumentException failure) { throw new IOException("Malformed transfer operation", failure); }
    if (!lines.getFirst().equals(operation.toString())) return "Prepared";
    return switch (lines.get(1)) {
      case "Prepared", "Committed", "RollingBack", "Transferred", "RolledBack", "RecoveryRequired" -> lines.get(1);
      default -> throw new IOException("Unknown transfer decision");
    };
  }

  static boolean terminal(String stage) { return stage.equals("Transferred") || stage.equals("RolledBack"); }
  static boolean destination(String stage) { return stage.equals("Committed"); }

  static void decision(Path snapshot, UUID operation, String stage) throws IOException {
    var target = statusFile(snapshot); var temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.writeString(temporary, operation + "\n" + stage + "\n", StandardCharsets.UTF_8);
      force(temporary);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally { Files.deleteIfExists(temporary); }
  }

  static void force(Path path) throws IOException {
    try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
  }

  static CompoundTag read(Path snapshot) throws IOException {
    var result = NbtIo.readCompressed(snapshot, NbtAccounter.create(TransferBackup.MAX_NBT_BYTES * 2L));
    if (result.getInt("Version") != 3 || !result.hasUUID("Operation")) throw new IOException("Unsupported transfer snapshot");
    return result;
  }

  private TransferJournal() {}
}
