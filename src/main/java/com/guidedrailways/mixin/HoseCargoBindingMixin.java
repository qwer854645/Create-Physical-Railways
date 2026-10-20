package com.guidedrailways.mixin;

import com.guidedrailways.CargoAdapters;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
      "com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity",
      "com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlockEntity",
      "com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector.ItemMagnetHoseConnectorBlockEntity"
    },
    remap = false)
public abstract class HoseCargoBindingMixin {
  @Inject(method = "<init>", at = @At("RETURN"))
  private void guidedrailways$bind(CallbackInfo ci) {
    CargoAdapters.bindConnector((BlockEntity) (Object) this);
  }

  // 0.2+ may resize or replace buffers while loading; older releases inherit onLoad.
  @Inject(method = "onLoad", at = @At("RETURN"), require = 0)
  private void guidedrailways$loaded(CallbackInfo ci) {
    CargoAdapters.bindConnector((BlockEntity) (Object) this);
  }
}
