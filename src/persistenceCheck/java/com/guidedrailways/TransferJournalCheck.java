package com.guidedrailways;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Crash boundaries for a reused body snapshot: decisions must belong to the current operation. */
public final class TransferJournalCheck {
  private static int checks;
  private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("guided-transfer-journal-");
    Path file = directory.resolve("body.nbt");
    UUID first = UUID.randomUUID(), next = UUID.randomUUID();
    try {
      check(TransferJournal.stage(file, first).equals("Prepared"), "no decision means uncommitted");
      var snapshot = new CompoundTag(); snapshot.putInt("Version", 3); snapshot.putUUID("Operation", first);
      var cargo = new CompoundTag(); cargo.putString("id", "minecraft:diamond"); cargo.putInt("count", 17); snapshot.put("Cargo", cargo);
      NbtIo.writeCompressed(snapshot, file);
      check(TransferJournal.read(file).getCompound("Cargo").getInt("count") == 17, "compressed snapshot round trip");
      for (String stage : List.of("Prepared", "Committed", "RollingBack", "Transferred", "RolledBack", "RecoveryRequired")) {
        TransferJournal.decision(file, first, stage);
        check(TransferJournal.stage(file, first).equals(stage), "atomic decision: " + stage);
        check(TransferJournal.stage(file, next).equals("Prepared"), "stale operation decision cannot commit a replacement snapshot: " + stage);
        check(TransferJournal.destination(stage) == stage.equals("Committed"), "replay direction: " + stage);
        check(TransferJournal.terminal(stage) == Set.of("Transferred", "RolledBack").contains(stage), "terminal replay exclusion: " + stage);
      }
      TransferJournal.decision(file, first, "Transferred");
      snapshot.putUUID("Operation", next); NbtIo.writeCompressed(snapshot, file);
      check(TransferJournal.stage(file, TransferJournal.read(file).getUUID("Operation")).equals("Prepared"), "crash between replacing snapshot and decision rolls back the new operation");
      TransferJournal.decision(file, next, "Prepared");
      check(!TransferJournal.destination(TransferJournal.stage(file, next)), "crash before commit restores source");
      TransferJournal.decision(file, next, "Committed");
      check(TransferJournal.destination(TransferJournal.stage(file, next)), "crash after commit restores destination");
      TransferJournal.decision(file, next, "RollingBack");
      check(!TransferJournal.destination(TransferJournal.stage(file, next)), "crash during rollback keeps source authoritative");
      Files.writeString(TransferJournal.statusFile(file), next + "\nInvalid\n");
      try { TransferJournal.stage(file, next); throw new AssertionError("accepted unknown decision"); }
      catch (java.io.IOException expected) { checks++; }
      Files.writeString(TransferJournal.statusFile(file), next + "\n");
      try { TransferJournal.stage(file, next); throw new AssertionError("truncated decision must not trigger an old inventory replay"); }
      catch (java.io.IOException expected) { checks++; }
      Files.writeString(TransferJournal.statusFile(file), "x".repeat(257));
      try { TransferJournal.stage(file, next); throw new AssertionError("accepted oversized decision"); }
      catch (java.io.IOException expected) { checks++; }
      snapshot.putInt("Version", 99); NbtIo.writeCompressed(snapshot, file);
      try { TransferJournal.read(file); throw new AssertionError("accepted unknown snapshot schema"); }
      catch (java.io.IOException expected) { checks++; }
      check(!Files.exists(TransferJournal.statusFile(file).resolveSibling("body.nbt.status.tmp")), "no temporary decision left behind");
      System.out.println("TransferJournalCheck: " + checks + " assertions passed");
    } finally {
      Files.deleteIfExists(TransferJournal.statusFile(file).resolveSibling("body.nbt.status.tmp"));
      Files.deleteIfExists(TransferJournal.statusFile(file)); Files.deleteIfExists(file); Files.deleteIfExists(directory);
    }
  }
}
