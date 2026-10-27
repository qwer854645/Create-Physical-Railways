package com.guidedrailways;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaterniond;

/**
 * Saved on the moving plate so late lower-body loading cannot lose the authorized destination.
 *
 * <p>{@code sourceRevision} is the claim revision observed when the move was authorized, or
 * {@link MountOwnership#UNKNOWN_REVISION} when the lower body was not loaded yet. Carrying it turns
 * the commit into a compare-and-set: a handoff that waited through another move of the same body is
 * rejected instead of silently rewriting a newer claim.
 */
record MountHandoff(
    MountOwnership.Location source,
    MountOwnership.Location destination,
    Quaterniond worldReference,
    long sourceRevision) {
  enum Result {
    WAITING,
    REJECTED,
    READY
  }

  static MountHandoff begin(
      MountHandoff pending,
      MountOwnership.Location source,
      MountOwnership.Location destination,
      Quaterniond worldReference,
      long sourceRevision) {
    return pending != null && pending.destination.equals(source)
        ? new MountHandoff(
            pending.source, destination, pending.worldReference, pending.sourceRevision)
        : new MountHandoff(source, destination, worldReference, sourceRevision);
  }

  /** Transient form after the claim committed; the caller clears the handoff right after. */
  MountHandoff ownershipCommitted() {
    return source.equals(destination)
        ? this
        : new MountHandoff(destination, destination, worldReference, sourceRevision);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.put("Source", BogieMountBinding.encode(source));
    tag.put("Destination", BogieMountBinding.encode(destination));
    tag.putLong("SourceRevision", sourceRevision);
    if (worldReference != null) {
      var ref = new CompoundTag();
      ref.putDouble("X", worldReference.x);
      ref.putDouble("Y", worldReference.y);
      ref.putDouble("Z", worldReference.z);
      ref.putDouble("W", worldReference.w);
      tag.put("WorldReference", ref);
    }
    return tag;
  }

  static MountHandoff load(CompoundTag tag) {
    var source = BogieMountBinding.decode(tag.getCompound("Source"));
    var destination = BogieMountBinding.decode(tag.getCompound("Destination"));
    if (source == null || destination == null) return null;
    // A handoff saved before revisions existed keeps its old meaning and stays unverified.
    long sourceRevision =
        tag.contains("SourceRevision", Tag.TAG_LONG)
            ? tag.getLong("SourceRevision")
            : MountOwnership.UNKNOWN_REVISION;
    Quaterniond reference = null;
    if (tag.contains("WorldReference")) {
      var ref = tag.getCompound("WorldReference");
      reference =
          new Quaterniond(
              ref.getDouble("X"), ref.getDouble("Y"), ref.getDouble("Z"), ref.getDouble("W"));
      if (!reference.isFinite() || reference.lengthSquared() < .5) return null;
      reference.normalize();
    }
    return new MountHandoff(source, destination, reference, sourceRevision);
  }
}
