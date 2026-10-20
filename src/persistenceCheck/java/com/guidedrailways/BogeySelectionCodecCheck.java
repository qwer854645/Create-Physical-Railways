package com.guidedrailways;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class BogeySelectionCodecCheck {
  public static void main(String[] args) {
    int count = 0;
    for (var variant : BogeyVariant.values()) {
      if (variant == BogeyVariant.DEFAULT) continue;
      var selected =
          new BogeySelectionNetwork.Select(
              ResourceLocation.parse(variant.id), ResourceLocation.parse(variant.size));
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        BogeySelectionNetwork.Select.CODEC.encode(buffer, selected);
        var restored = BogeySelectionNetwork.Select.CODEC.decode(buffer);
        if (!restored.equals(selected) || buffer.readableBytes() != 0)
          throw new AssertionError("physical menu lost style or size: " + variant);
        count++;
      } finally {
        buffer.release();
      }
    }
    System.out.println(
        "BogeySelectionCodecCheck: " + count + " exact style/size network round trips passed");
  }
}
