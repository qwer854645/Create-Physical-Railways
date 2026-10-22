package com.guidedrailways;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** A saved move request carried by the endpoint, not a second connection registry. */
record CouplerHandoff(
    UUID endpoint,
    String dimension,
    long source,
    long destination,
    Vector3d worldAnchor,
    Quaterniond worldFrame,
    double length) {
  enum Result {
    WAITING,
    REJECTED,
    READY
  }

  static CouplerHandoff begin(
      CouplerHandoff pending,
      UUID endpoint,
      String dimension,
      long source,
      long destination,
      Vector3d anchor,
      Quaterniond frame,
      double length) {
    if (pending != null && pending.matches(endpoint, dimension, source))
      return new CouplerHandoff(
          endpoint,
          dimension,
          pending.source,
          destination,
          pending.worldAnchor,
          pending.worldFrame,
          pending.length);
    return new CouplerHandoff(
        endpoint,
        dimension,
        source,
        destination,
        anchor == null ? null : new Vector3d(anchor),
        frame == null ? null : new Quaterniond(frame),
        length);
  }

  boolean matches(UUID id, String world, long pos) {
    return endpoint.equals(id) && dimension.equals(world) && destination == pos;
  }

  boolean protectsSource(UUID id, String world, long pos) {
    return endpoint.equals(id) && dimension.equals(world) && source == pos && destination != source;
  }

  Result commit(CouplerSavedData data, UUID id, String world, BlockPos pos, boolean geometryReady) {
    if (!authorizes(data, id, world, pos)) return Result.REJECTED;
    if (worldAnchor != null && !geometryReady) return Result.WAITING;
    return data.move(id, BlockPos.of(source), pos) ? Result.READY : Result.REJECTED;
  }

  boolean authorizes(CouplerSavedData data, UUID id, String world, BlockPos pos) {
    return matches(id, world, pos.asLong())
        && (data.owns(id, BlockPos.of(source)) || data.owns(id, pos));
  }

  CouplerHandoff ownershipCommitted() {
    return source == destination
        ? this
        : new CouplerHandoff(
            endpoint, dimension, destination, destination, worldAnchor, worldFrame, length);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.putUUID("Endpoint", endpoint);
    tag.putString("Dimension", dimension);
    tag.putLong("Source", source);
    tag.putLong("Destination", destination);
    if (worldAnchor != null) {
      var geometry = new CompoundTag();
      geometry.putDouble("X", worldAnchor.x);
      geometry.putDouble("Y", worldAnchor.y);
      geometry.putDouble("Z", worldAnchor.z);
      geometry.putDouble("Qx", worldFrame.x);
      geometry.putDouble("Qy", worldFrame.y);
      geometry.putDouble("Qz", worldFrame.z);
      geometry.putDouble("Qw", worldFrame.w);
      geometry.putDouble("Length", length);
      tag.put("Geometry", geometry);
    }
    return tag;
  }

  static CouplerHandoff load(CompoundTag tag) {
    if (!tag.hasUUID("Endpoint")
        || !tag.contains("Dimension", Tag.TAG_STRING)
        || tag.getString("Dimension").isEmpty()
        || !tag.contains("Source", Tag.TAG_LONG)
        || !tag.contains("Destination", Tag.TAG_LONG)) return null;
    Vector3d anchor = null;
    Quaterniond frame = null;
    double length = -1;
    if (tag.contains("Geometry")) {
      var geometry = tag.getCompound("Geometry");
      anchor =
          new Vector3d(geometry.getDouble("X"), geometry.getDouble("Y"), geometry.getDouble("Z"));
      frame =
          new Quaterniond(
              geometry.getDouble("Qx"),
              geometry.getDouble("Qy"),
              geometry.getDouble("Qz"),
              geometry.getDouble("Qw"));
      length = geometry.getDouble("Length");
      if (!anchor.isFinite()
          || !frame.isFinite()
          || frame.lengthSquared() < .5
          || !Double.isFinite(length)
          || length <= 0) return null;
      frame.normalize();
    }
    return new CouplerHandoff(
        tag.getUUID("Endpoint"),
        tag.getString("Dimension"),
        tag.getLong("Source"),
        tag.getLong("Destination"),
        anchor,
        frame,
        length);
  }
}
