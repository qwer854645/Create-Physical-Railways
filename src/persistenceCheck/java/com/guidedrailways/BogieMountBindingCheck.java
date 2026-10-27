package com.guidedrailways;

import java.io.*;
import net.minecraft.nbt.*;

/** Exercise the actual body ownership serializer without a physical world or mod loading. */
public final class BogieMountBindingCheck {
  private static int checks;

  private static final long UNKNOWN = MountOwnership.UNKNOWN_REVISION;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static CompoundTag reload(CompoundTag tag) throws IOException {
    var bytes = new ByteArrayOutputStream();
    NbtIo.write(tag, new DataOutputStream(bytes));
    return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  }

  private static long revision(CompoundTag data) {
    var owner = BogieMountBinding.owner(data);
    return owner == null ? UNKNOWN : owner.revision();
  }

  private static boolean transfer(
      CompoundTag data, MountOwnership.Location source, MountOwnership.Location destination,
      long expectedRevision) {
    var result = BogieMountBinding.move(data, source, destination, expectedRevision);
    return result == MountOwnership.Result.APPLIED || result == MountOwnership.Result.REPLAYED;
  }

  public static void main(String[] args) throws Exception {
    var original = new MountOwnership.Location("minecraft:overworld", 123456L);
    var copy = new MountOwnership.Location("minecraft:overworld", 123457L);
    var moved = new MountOwnership.Location("minecraft:overworld", 987654L);
    var otherDimension = new MountOwnership.Location("minecraft:the_nether", 123456L);
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
    check(
        !transfer(data, copy, moved, UNKNOWN),
        "foreign plate cannot authorize a handoff");
    check(
        BogieMountBinding.matches(data, original), "failed claims leave original ownership intact");
    check(
        transfer(data, original, moved, UNKNOWN),
        "actual move hands ownership to destination");
    data = reload(data);
    check(
        !BogieMountBinding.claim(data, original),
        "old position cannot reclaim after move and reload");
    check(BogieMountBinding.claim(data, moved), "destination restores after reload");
    check(transfer(data, original, moved, UNKNOWN), "repeated restore is idempotent");
    check(
        transfer(data, moved, original, UNKNOWN),
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
    var pending = MountHandoff.begin(null, original, moved, reference, UNKNOWN);
    pending = MountHandoff.load(reload(pending.save()));
    check(
        pending != null && pending.source().equals(original) && pending.destination().equals(moved),
        "pending move survives an actual NBT disk round trip");
    check(
        pending.worldReference().equals(reference, 1e-12),
        "pending move preserves world pivot reference");
    var finalDestination = new MountOwnership.Location("minecraft:overworld", 246810L);
    var secondMove =
        MountHandoff.begin(pending, moved, finalDestination, new org.joml.Quaterniond(), UNKNOWN);
    check(
        secondMove.source().equals(original) && secondMove.destination().equals(finalDestination),
        "multiple moves before body loading retain the authorized original source");
    check(
        secondMove.worldReference().equals(reference, 1e-12),
        "queued moves retain the original world reference");
    check(
        transfer(data, secondMove.source(), secondMove.destination(), UNKNOWN),
        "delayed lower-body loading completes the composed handoff");
    var copiedMove = MountHandoff.begin(pending, copy, moved, new org.joml.Quaterniond(), UNKNOWN);
    check(
        copiedMove.source().equals(copy),
        "a copied pending plate cannot reuse another source proof");
    check(
        !transfer(data, copiedMove.source(), copiedMove.destination(), UNKNOWN),
        "foreign pending move cannot steal the body");
    check(MountHandoff.load(new CompoundTag()) == null, "absent handoff has no authority");
    var partialData = new CompoundTag();
    check(BogieMountBinding.claim(partialData, original), "partial scenario starts at original");
    var partial = MountHandoff.begin(null, original, moved, reference, UNKNOWN);
    check(
        transfer(partialData, partial.source(), partial.destination(), UNKNOWN),
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
            partial, moved, finalDestination, new org.joml.Quaterniond().rotateY(-.5), UNKNOWN);
    check(
        afterPartial.source().equals(moved),
        "next move starts at B after A-to-B ownership committed");
    check(
        afterPartial.worldReference().equals(reference, 1e-12),
        "saved partial handoff retains the original world pivot reference");
    check(
        transfer(
            partialData, afterPartial.source(), afterPartial.destination(), UNKNOWN),
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
            new org.joml.Quaterniond(),
            UNKNOWN);
    check(
        transfer(partialData, reverse.source(), reverse.destination(), UNKNOWN),
        "reversing a partially completed move uses the latest committed owner");

    // Revisions: a reused location must stay distinguishable from the claim it replaced.
    var observed = new CompoundTag();
    check(BogieMountBinding.claim(observed, original), "observed scenario starts at original");
    check(revision(observed) == 0, "a fresh claim records revision 0");
    check(
        transfer(observed, original, moved, 0),
        "the observed source revision authorizes the first move");
    observed = reload(observed);
    check(revision(observed) == 1, "revision survives the actual disk round trip");
    check(
        transfer(observed, moved, original, 1),
        "the return move is authorized by the revision it observed");
    var homeOwner = BogieMountBinding.owner(observed);
    check(
        homeOwner != null && homeOwner.matches(original) && homeOwner.revision() == 2,
        "A-to-B-to-A is revision 2, not the claim that started there");
    var beforeStale = observed.copy();
    check(
        !transfer(observed, original, finalDestination, 0),
        "a handoff authorized at revision 0 cannot rewrite a claim that reached revision 2");
    check(observed.equals(beforeStale), "a rejected handoff writes nothing");
    check(
        BogieMountBinding.matches(observed, original),
        "a rejected handoff leaves the body with its current owner");
    var staleHandoff = MountHandoff.begin(null, original, finalDestination, null, 0);
    check(
        !transfer(
            observed, staleHandoff.source(), staleHandoff.destination(), staleHandoff.sourceRevision()),
        "a pending handoff that outlived the return move is rejected end to end");

    var unknownSource = new CompoundTag();
    BogieMountBinding.claim(unknownSource, original);
    check(
        transfer(unknownSource, original, moved, UNKNOWN),
        "an unobservable source keeps the location-only behaviour");

    var legacy = new CompoundTag();
    legacy.put(BogieMountBinding.KEY, BogieMountBinding.encode(original));
    check(revision(legacy) == 0, "a tag written before revisions existed reads as revision 0");
    check(
        transfer(legacy, original, moved, 0),
        "a legacy body still moves once against the assumed revision");

    var maxed = new CompoundTag();
    var maxOwner = BogieMountBinding.encode(original);
    maxOwner.putLong("Revision", Long.MAX_VALUE);
    maxed.put(BogieMountBinding.KEY, maxOwner);
    check(
        !transfer(maxed, original, moved, Long.MAX_VALUE),
        "a revision that cannot advance is rejected before any write");

    var observedHandoff = MountHandoff.begin(null, original, moved, reference, 5);
    var restored = MountHandoff.load(reload(observedHandoff.save()));
    check(
        restored != null && restored.sourceRevision() == 5,
        "a handoff keeps the source revision it was authorized at");
    var legacyHandoff = new CompoundTag();
    legacyHandoff.put("Source", BogieMountBinding.encode(original));
    legacyHandoff.put("Destination", BogieMountBinding.encode(moved));
    var loadedLegacy = MountHandoff.load(legacyHandoff);
    check(
        loadedLegacy != null && loadedLegacy.sourceRevision() == UNKNOWN,
        "a handoff saved before revisions existed stays unverified");
    var chained =
        MountHandoff.begin(observedHandoff, moved, finalDestination, new org.joml.Quaterniond(), 9);
    check(
        chained.source().equals(original) && chained.sourceRevision() == 5,
        "a chained move keeps the original source and its revision");

    System.out.println(
        "BogieMountBindingCheck: "
            + checks
            + " saved ownership, revision, copy-first reload, foreign claims and handoff checks"
            + " passed");
  }
}
