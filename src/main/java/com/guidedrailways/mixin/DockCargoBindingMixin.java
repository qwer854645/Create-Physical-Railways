package com.guidedrailways.mixin;

import com.guidedrailways.CargoAdapters;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity",
    remap = false)
public abstract class DockCargoBindingMixin {
  @Inject(method = "initialize", at = @At("RETURN"))
  private void guidedrailways$bind(CallbackInfo ci) {
    CargoAdapters.bindConnector((BlockEntity) (Object) this);
  }
}
