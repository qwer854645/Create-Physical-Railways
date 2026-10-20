package com.guidedrailways.mixin;

import com.guidedrailways.BlocksBogiesClient;
import com.weido.create_bb.data.menu.Entry.StyleEntry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.weido.create_bb.data.menu.BogieStyleSelectionScreen", remap = false)
public abstract class BlocksBogiesMenuMixin {
  @Shadow StyleEntry selectedBogey;
  @Shadow private com.simibubi.create.foundation.gui.widget.ScrollInput sizeScroll;

  @Inject(method = "onMenuClose", at = @At("HEAD"))
  private void guidedrailways$selection(CallbackInfo ci) {
    var player = net.minecraft.client.Minecraft.getInstance().player;
    if (player != null
        && player.getMainHandItem().getItem() instanceof com.guidedrailways.BogieItem
        && selectedBogey != null)
      BlocksBogiesClient.selection(
          selectedBogey.bogeyStyle(),
          ((com.weido.create_bb.data.menu.Input.SizeScrollInput) sizeScroll).getCurrentSize());
  }
}
