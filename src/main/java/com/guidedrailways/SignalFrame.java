package com.guidedrailways;

import java.util.*;
import net.minecraft.nbt.*;

/** Bounded, immutable section identities; no inventory or railway graph is transmitted. */
record SignalFrame(long revision, boolean known, Set<UUID> occupied, Set<UUID> claimed) {
  static final int LIMIT = 4096;
  static final SignalFrame UNKNOWN = new SignalFrame(0, false, Set.of(), Set.of());

  SignalFrame {
    if (revision < 0 || occupied.size() > LIMIT || claimed.size() > LIMIT)
      throw new IllegalArgumentException("Invalid signal frame");
    occupied = known ? Set.copyOf(occupied) : Set.of();
    claimed = known ? Set.copyOf(claimed) : Set.of();
  }

  CompoundTag write() {
    var tag = new CompoundTag();
    tag.putLong("Revision", revision);
    tag.putBoolean("Known", known);
    tag.putLongArray("Occupied", pack(occupied));
    tag.putLongArray("Claimed", pack(claimed));
    return tag;
  }

  boolean resolvable(Set<UUID> available) {
    return known && available.containsAll(occupied) && available.containsAll(claimed);
  }

  static SignalFrame read(CompoundTag tag) {
    if (!tag.contains("Revision", Tag.TAG_LONG)
        || !tag.contains("Known", Tag.TAG_BYTE)
        || !tag.contains("Occupied", Tag.TAG_LONG_ARRAY)
        || !tag.contains("Claimed", Tag.TAG_LONG_ARRAY))
      throw new IllegalArgumentException("Incomplete signal frame");
    return new SignalFrame(
        tag.getLong("Revision"),
        tag.getBoolean("Known"),
        unpack(tag.getLongArray("Occupied")),
        unpack(tag.getLongArray("Claimed")));
  }

  private static long[] pack(Set<UUID> ids) {
    long[] array = new long[ids.size() * 2];
    int i = 0;
    for (var id : ids.stream().sorted().toList()) {
      array[i++] = id.getMostSignificantBits();
      array[i++] = id.getLeastSignificantBits();
    }
    return array;
  }

  private static Set<UUID> unpack(long[] array) {
    if (array.length % 2 != 0 || array.length > LIMIT * 2)
      throw new IllegalArgumentException("Invalid section UUID array");
    var ids = new HashSet<UUID>();
    for (int i = 0; i < array.length; i += 2) ids.add(new UUID(array[i], array[i + 1]));
    return Set.copyOf(ids);
  }
}
