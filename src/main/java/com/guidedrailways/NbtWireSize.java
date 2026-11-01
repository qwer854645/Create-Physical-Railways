package com.guidedrailways;

import net.minecraft.nbt.*;

/** Uncompressed Java-edition NBT size, without allocating or encoding a second payload. */
final class NbtWireSize {
  static long packet(CompoundTag tag) { return 3 + payload(tag); }
  static long named(String key, Tag tag) { return 1 + utf(key) + payload(tag); }

  static long payload(Tag tag) {
    return switch (tag.getId()) {
      case Tag.TAG_END -> 0;
      case Tag.TAG_BYTE -> 1;
      case Tag.TAG_SHORT -> 2;
      case Tag.TAG_INT, Tag.TAG_FLOAT -> 4;
      case Tag.TAG_LONG, Tag.TAG_DOUBLE -> 8;
      case Tag.TAG_BYTE_ARRAY -> 4L + ((ByteArrayTag) tag).size();
      case Tag.TAG_INT_ARRAY -> 4L + 4L * ((IntArrayTag) tag).size();
      case Tag.TAG_LONG_ARRAY -> 4L + 8L * ((LongArrayTag) tag).size();
      case Tag.TAG_STRING -> utf(tag.getAsString());
      case Tag.TAG_LIST -> {
        long size = 5;
        for (var value : (ListTag) tag) size += payload(value);
        yield size;
      }
      case Tag.TAG_COMPOUND -> {
        long size = 1;
        var compound = (CompoundTag) tag;
        for (var key : compound.getAllKeys()) size += named(key, compound.get(key));
        yield size;
      }
      default -> throw new IllegalArgumentException("Unknown NBT type: " + tag.getId());
    };
  }

  /** DataOutput.writeUTF uses modified UTF-8, including two bytes for NUL and surrogate units. */
  private static long utf(String value) {
    long size = 2;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      size += c >= 1 && c <= 127 ? 1 : c <= 2047 ? 2 : 3;
    }
    return size;
  }

  private NbtWireSize() {}
}
