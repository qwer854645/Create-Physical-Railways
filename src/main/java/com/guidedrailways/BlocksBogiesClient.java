package com.guidedrailways;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.network.chat.Component;

public final class BlocksBogiesClient {
  public static void selection(
      BogeyStyle style, com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize size) {
    PhysicalBogeySelection.selectedName = style.displayName.copy();
    if (size != null)
      net.neoforged.neoforge.network.PacketDistributor.sendToServer(
          new BogeySelectionNetwork.Select(style.id, size.id()));
  }

  static void register() {
    PhysicalBogeySelection.menu =
        () ->
            net.createmod.catnip.gui.ScreenOpener.open(
                new com.weido.create_bb.data.menu.BogieStyleSelectionScreen(null));
    var previous = PhysicalBogeySelection.tooltip;
    PhysicalBogeySelection.tooltip =
        lines -> {
          if (previous != null) previous.accept(lines);
          lines.add(
              Component.translatable("tooltip.guidedrailways.bogey.bnb")
                  .withStyle(net.minecraft.ChatFormatting.GRAY));
          if (previous == null && PhysicalBogeySelection.selectedName != null)
            lines.add(
                Component.translatable(
                        "tooltip.guidedrailways.bogey.selected",
                        PhysicalBogeySelection.selectedName)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
            PhysicalBogeySelection.selectedName = null);
  }

  private BlocksBogiesClient() {}
}
