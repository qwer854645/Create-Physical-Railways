package com.guidedrailways;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The lower body's saved owner is a location, which copied plate NBT cannot impersonate. */
final class BogieMountBinding {
  static final String KEY = "GuidedMount";

  record Location(String dimension, long position) {}

  static boolean matches(CompoundTag data, Location location) {
    if (data == null || !data.contains(KEY, Tag.TAG_COMPOUND)) return false;
    var owner = data.getCompound(KEY);
    return owner.contains("Position", Tag.TAG_LONG)
        && owner.getString("Dimension").equals(location.dimension())
        && owner.getLong("Position") == location.position();
  }

  static boolean claim(CompoundTag data, Location location) {
    if (data.contains(KEY)) return matches(data, location);
    write(data, location);
    return true;
  }

  static boolean move(CompoundTag data, Location source, Location destination) {
    if (matches(data, destination)) return true;
    if (!claim(data, source)) return false;
    write(data, destination);
    return true;
  }

  private static void write(CompoundTag data, Location location) {
    data.put(KEY, encode(location));
  }

  static CompoundTag encode(Location location) {
    var owner = new CompoundTag();
    owner.putString("Dimension", location.dimension());
    owner.putLong("Position", location.position());
    return owner;
  }

  static Location decode(CompoundTag owner) {
    return owner.contains("Dimension", Tag.TAG_STRING) && owner.contains("Position", Tag.TAG_LONG)
        ? new Location(owner.getString("Dimension"), owner.getLong("Position"))
        : null;
  }

  private BogieMountBinding() {}
}
