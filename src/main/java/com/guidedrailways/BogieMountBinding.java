package com.guidedrailways;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Saves {@link MountOwnership} into the lower body's user data. The owner record carries the plate
 * location and the move revision, so a copied plate's NBT cannot impersonate the original and an
 * older handoff cannot rewrite a claim that has already moved on.
 */
final class BogieMountBinding {
  static final String KEY = "GuidedMount";

  /** Recorded claim, or {@code null} when this body was never bound. */
  static MountOwnership owner(CompoundTag data) {
    if (data == null || !data.contains(KEY, Tag.TAG_COMPOUND)) return null;
    var owner = data.getCompound(KEY);
    var location = decode(owner);
    return location == null ? null : new MountOwnership(location, revision(owner));
  }

  static boolean matches(CompoundTag data, MountOwnership.Location location) {
    var owner = owner(data);
    return owner != null && owner.matches(location);
  }

  static boolean claim(CompoundTag data, MountOwnership.Location location) {
    if (data.contains(KEY)) return matches(data, location);
    write(data, MountOwnership.firstClaim(location));
    return true;
  }

  /**
   * Applies a handoff as a compare-and-set on the recorded claim. {@code expectedRevision} is what
   * the caller observed when it authorized the move, or {@link MountOwnership#UNKNOWN_REVISION}
   * when the lower body was unobservable at that time.
   */
  static MountOwnership.Result move(
      CompoundTag data,
      MountOwnership.Location source,
      MountOwnership.Location destination,
      long expectedRevision) {
    var current = owner(data);
    if (current == null) return MountOwnership.Result.REJECTED_SOURCE;
    var outcome = current.moveTo(source, destination, expectedRevision);
    if (outcome.changed()) write(data, outcome.ownership());
    return outcome.result();
  }

  /** A tag written before revisions existed reads as revision 0, which still moves once. */
  static long revision(CompoundTag owner) {
    return owner.contains("Revision", Tag.TAG_LONG) ? Math.max(0, owner.getLong("Revision")) : 0;
  }

  private static void write(CompoundTag data, MountOwnership ownership) {
    var owner = encode(ownership.location());
    owner.putLong("Revision", ownership.revision());
    data.put(KEY, owner);
  }

  static CompoundTag encode(MountOwnership.Location location) {
    var owner = new CompoundTag();
    owner.putString("Dimension", location.dimension());
    owner.putLong("Position", location.position());
    return owner;
  }

  static MountOwnership.Location decode(CompoundTag owner) {
    return owner.contains("Dimension", Tag.TAG_STRING) && owner.contains("Position", Tag.TAG_LONG)
        ? new MountOwnership.Location(owner.getString("Dimension"), owner.getLong("Position"))
        : null;
  }

  private BogieMountBinding() {}
}
