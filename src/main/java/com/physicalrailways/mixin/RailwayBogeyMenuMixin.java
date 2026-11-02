package com.physicalrailways.mixin;

import com.physicalrailways.SteamRailwaysClient;
import com.railwayteam.railways.api.bogeymenu.v0.entry.BogeyEntry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe confirmed native selection, leaving its packet and menu intact. Client only. */
@Pseudo
@Mixin(targets = "com.railwayteam.railways.content.bogey_menu.BogeyMenuScreen", remap = false)
public abstract class RailwayBogeyMenuMixin extends net.createmod.catnip.gui.AbstractSimiScreen {
  @Shadow BogeyEntry selectedBogey;
  @Shadow @Final private com.railwayteam.railways.registry.CRGuiTextures background;
  @Unique private net.minecraft.client.gui.components.Button physicalrailways$size;

  @Inject(method = "init", at = @At("TAIL"))
  private void physicalrailways$sizeButton(CallbackInfo ci) {
    var mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.player == null
        || !(mc.player.getMainHandItem().getItem() instanceof com.physicalrailways.BogieItem)) return;
    int x = guiLeft + background.width + 4, y = guiTop + background.height - 20;
    if (x + 104 > width) {
      x = guiLeft;
      y = Math.min(height - 20, guiTop + background.height + 4);
    }
    physicalrailways$size =
        addRenderableWidget(
            SteamRailwaysClient.sizeButton(
                x, y, () -> selectedBogey == null ? null : selectedBogey.bogeyStyle()));
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void physicalrailways$refresh(CallbackInfo ci) {
    if (physicalrailways$size != null)
      SteamRailwaysClient.refreshSize(
          physicalrailways$size, selectedBogey == null ? null : selectedBogey.bogeyStyle());
  }

  @ModifyArg(
      method = "onMenuClose",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/railwayteam/railways/util/packet/BogeyStyleSelectionPacket;<init>(Lcom/simibubi/create/content/trains/bogey/BogeyStyle;Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;)V"),
      index = 1)
  private com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize physicalrailways$size(
      com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize original) {
    return selectedBogey == null
        ? original
        : SteamRailwaysClient.size(selectedBogey.bogeyStyle(), original);
  }

  @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
      method = "renderWindow",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/railwayteam/railways/content/bogey_menu/handler/BogeyMenuHandlerClient;getRenderCycle(Lcom/simibubi/create/content/trains/bogey/BogeyStyle;)Ljava/util/List;"))
  private java.util.List<
          net.createmod.catnip.data.Pair<
              com.simibubi.create.content.trains.bogey.BogeyStyle,
              com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize>>
      physicalrailways$preview(
          java.util.List<
                  net.createmod.catnip.data.Pair<
                      com.simibubi.create.content.trains.bogey.BogeyStyle,
                      com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize>>
              original) {
    var mc = net.minecraft.client.Minecraft.getInstance();
    if (selectedBogey == null
        || mc.player == null
        || !(mc.player.getMainHandItem().getItem() instanceof com.physicalrailways.BogieItem))
      return original;
    var style = selectedBogey.bogeyStyle();
    var size =
        SteamRailwaysClient.size(
            style,
            com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerClient.getSize(
                style));
    return size == null
        ? original
        : java.util.List.of(net.createmod.catnip.data.Pair.of(style, size));
  }

  @Inject(method = "onMenuClose", at = @At("HEAD"))
  private void physicalrailways$selection(CallbackInfo ci) {
    if (selectedBogey != null) SteamRailwaysClient.selection(selectedBogey.bogeyStyle());
  }
}
