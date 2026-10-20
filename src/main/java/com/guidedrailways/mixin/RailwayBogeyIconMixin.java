package com.guidedrailways.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pinned Beta omits several menu PNGs; use its own generic bogey icon instead of missing
 * texture.
 */
@Pseudo
@Mixin(targets = "com.railwayteam.railways.api.bogeymenu.v0.entry.BogeyEntry", remap = false)
public abstract class RailwayBogeyIconMixin {
  @Inject(method = "iconLocation", at = @At("RETURN"), cancellable = true)
  private void guidedrailways$icon(CallbackInfoReturnable<ResourceLocation> cir) {
    var requested = cir.getReturnValue();
    if (requested != null
        && requested.getNamespace().equals("railways")
        && Minecraft.getInstance().getResourceManager().getResource(requested).isEmpty())
      cir.setReturnValue(
          ResourceLocation.fromNamespaceAndPath(
              "railways", "textures/gui/bogey_icons/default_icon.png"));
  }
}
