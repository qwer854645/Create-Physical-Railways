package com.guidedrailways;

import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;

/** Saved on the moving plate so late lower-body loading cannot lose the authorized destination. */
record MountHandoff(
    BogieMountBinding.Location source,
    BogieMountBinding.Location destination,
    Quaterniond worldReference) {
  enum Result {
    WAITING,
    REJECTED,
    READY
  }

  static MountHandoff begin(
      MountHandoff pending,
      BogieMountBinding.Location source,
      BogieMountBinding.Location destination,
      Quaterniond worldReference) {
    return pending != null && pending.destination.equals(source)
        ? new MountHandoff(pending.source, destination, pending.worldReference)
        : new MountHandoff(source, destination, worldReference);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.put("Source", BogieMountBinding.encode(source));
    tag.put("Destination", BogieMountBinding.encode(destination));
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
    Quaterniond reference = null;
    if (tag.contains("WorldReference")) {
      var ref = tag.getCompound("WorldReference");
      reference =
          new Quaterniond(
              ref.getDouble("X"), ref.getDouble("Y"), ref.getDouble("Z"), ref.getDouble("W"));
      if (!reference.isFinite() || reference.lengthSquared() < .5) return null;
      reference.normalize();
    }
    return new MountHandoff(source, destination, reference);
  }
}
