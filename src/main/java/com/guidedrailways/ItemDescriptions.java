package com.guidedrailways;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Native Create descriptions provide its Shift prompt, wrapping, palette and locale cache. */
final class ItemDescriptions {
  static void register(IEventBus bus) {
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> {
                  describe(Content.DRIVER_ITEM.get());
                  describe(Content.COUPLER_ITEM.get());
                  describe(Content.COUPLER_LINK.get());
                  var bogie = Content.BOGIE_ITEM.get();
                  TooltipModifier.REGISTRY.register(
                      bogie,
                      new ItemDescription.Modifier(bogie, FontHelper.Palette.STANDARD_CREATE)
                          .andThen(
                              eventTip -> {
                                if (Screen.hasShiftDown() && PhysicalBogeySelection.tooltip != null)
                                  PhysicalBogeySelection.tooltip.accept(eventTip.getToolTip());
                              }));
                  if (ModList.get().isLoaded("simulated")) describe(RailDockContent.ITEM.get());
                }));
  }

  private static void describe(Item item) {
    TooltipModifier.REGISTRY.register(
        item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
  }

  private ItemDescriptions() {}
}
