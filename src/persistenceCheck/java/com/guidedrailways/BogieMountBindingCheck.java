package com.guidedrailways;

import java.io.*;
import net.minecraft.nbt.*;

/** Exercise the actual body ownership serializer without a physical world or mod loading. */
public final class BogieMountBindingCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static CompoundTag reload(CompoundTag tag) throws IOException {
    var bytes = new ByteArrayOutputStream();
    NbtIo.write(tag, new DataOutputStream(bytes));
    return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  }

  public static void main(String[] args) throws Exception {
    var original = new BogieMountBinding.Location("minecraft:overworld", 123456L);
    var copy = new BogieMountBinding.Location("minecraft:overworld", 123457L);
    var moved = new BogieMountBinding.Location("minecraft:overworld", 987654L);
    var otherDimension = new BogieMountBinding.Location("minecraft:the_nether", 123456L);
    var data = new CompoundTag();
    data.putBoolean("GuidedDerailed", true);
    data.putString("CollisionCatalog", "shape_catalog_v1");
    check(BogieMountBinding.claim(data, original), "fresh body accepts its real platform");
    data = reload(data);
    check(!BogieMountBinding.claim(data, copy), "copy loading first cannot claim a saved body");
    check(BogieMountBinding.claim(data, original), "original loading later retains its body");
    check(
        !BogieMountBinding.claim(data, otherDimension),
        "same position in another dimension is distinct");
    check(!BogieMountBinding.move(data, copy, moved), "foreign plate cannot authorize a handoff");
    check(
        BogieMountBinding.matches(data, original), "failed claims leave original ownership intact");
    check(
        BogieMountBinding.move(data, original, moved),
        "actual move hands ownership to destination");
    data = reload(data);
    check(
        !BogieMountBinding.claim(data, original),
        "old position cannot reclaim after move and reload");
    check(BogieMountBinding.claim(data, moved), "destination restores after reload");
    check(BogieMountBinding.move(data, original, moved), "repeated restore is idempotent");
    check(
        BogieMountBinding.move(data, moved, original),
        "a reverse assembly can hand ownership back");
    check(
        data.getBoolean("GuidedDerailed")
            && data.getString("CollisionCatalog").equals("shape_catalog_v1"),
        "binding preserves other body data");
    var damaged = new CompoundTag();
    damaged.put(BogieMountBinding.KEY, new CompoundTag());
    check(
        !BogieMountBinding.claim(damaged, original),
        "malformed saved owner is not silently reassigned");
    var reference = new org.joml.Quaterniond().rotateY(.7);
    var pending = MountHandoff.begin(null, original, moved, reference);
    pending = MountHandoff.load(reload(pending.save()));
    check(
        pending != null && pending.source().equals(original) && pending.destination().equals(moved),
        "pending move survives an actual NBT disk round trip");
    check(
        pending.worldReference().equals(reference, 1e-12),
        "pending move preserves world pivot reference");
    var finalDestination = new BogieMountBinding.Location("minecraft:overworld", 246810L);
    var secondMove =
        MountHandoff.begin(pending, moved, finalDestination, new org.joml.Quaterniond());
    check(
        secondMove.source().equals(original) && secondMove.destination().equals(finalDestination),
        "multiple moves before body loading retain the authorized original source");
    check(
        secondMove.worldReference().equals(reference, 1e-12),
        "queued moves retain the original world reference");
    check(
        BogieMountBinding.move(data, secondMove.source(), secondMove.destination()),
        "delayed lower-body loading completes the composed handoff");
    var copiedMove = MountHandoff.begin(pending, copy, moved, new org.joml.Quaterniond());
    check(
        copiedMove.source().equals(copy),
        "a copied pending plate cannot reuse another source proof");
    check(
        !BogieMountBinding.move(data, copiedMove.source(), copiedMove.destination()),
        "foreign pending move cannot steal the body");
    check(MountHandoff.load(new CompoundTag()) == null, "absent handoff has no authority");
    var partialData = new CompoundTag();
    check(BogieMountBinding.claim(partialData, original), "partial scenario starts at original");
    var partial = MountHandoff.begin(null, original, moved, reference);
    check(
        BogieMountBinding.move(partialData, partial.source(), partial.destination()),
        "ownership commits before the carrier is ready for pivot restoration");
    partial = partial.ownershipCommitted();
    check(
        partial.source().equals(moved) && partial.destination().equals(moved),
        "pending pivot restoration records the actual committed location");
    check(
        partial.ownershipCommitted() == partial,
        "repeated waiting does not rebuild committed state");
    partialData = reload(partialData);
    partial = MountHandoff.load(reload(partial.save()));
    var afterPartial =
        MountHandoff.begin(
            partial, moved, finalDestination, new org.joml.Quaterniond().rotateY(-.5));
    check(
        afterPartial.source().equals(moved),
        "next move starts at B after A-to-B ownership committed");
    check(
        afterPartial.worldReference().equals(reference, 1e-12),
        "saved partial handoff retains the original world pivot reference");
    check(
        BogieMountBinding.move(partialData, afterPartial.source(), afterPartial.destination()),
        "B-to-C handoff succeeds while the previous pivot restoration is pending");
    check(
        !BogieMountBinding.claim(partialData, original)
            && !BogieMountBinding.claim(partialData, moved),
        "old locations cannot reclaim after C commits");
    var reverse =
        MountHandoff.begin(
            afterPartial.ownershipCommitted(),
            finalDestination,
            original,
            new org.joml.Quaterniond());
    check(
        BogieMountBinding.move(partialData, reverse.source(), reverse.destination()),
        "reversing a partially completed move uses the latest committed owner");
    System.out.println(
        "BogieMountBindingCheck: "
            + checks
            + " saved ownership, copy-first reload, foreign claims and assembly handoff checks passed");
  }
}
