package com.guidedrailways;

import com.weido.create_bb.data.menu.Entry.StyleMenuHandler;

final class BlocksBogies {
  static void register() {
    if (net.neoforged.fml.ModList.get().isLoaded("railways")) return;
    PhysicalBogeySelection.bridge =
        player -> {
          var selected = StyleMenuHandler.getStyle(player.getUUID());
          var style = selected.getFirst();
          var size = selected.getSecond();
          if (size == null) size = style.validSizes().iterator().next();
          var variant = BogeyVariant.find(style.id.toString(), size.id().toString());
          if (variant == null)
            throw new IllegalArgumentException("message.guidedrailways.bogey.unsupported");
          return new PhysicalBogeySelection.Selection(
              variant.gauge, variant, style.defaultData.copy());
        };
  }

  private BlocksBogies() {}
}
