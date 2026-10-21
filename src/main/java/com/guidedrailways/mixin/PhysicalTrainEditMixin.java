package com.guidedrailways.mixin;

import com.guidedrailways.PhysicalTrainProxy;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.station.TrainEditPacket;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native editing changes only the temporary query object, never the actual controls. */
@Mixin(value = TrainEditPacket.class, remap = false)
public abstract class PhysicalTrainEditMixin {
  @Shadow @Final protected UUID id;

  @Inject(method = "handleSided", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$keepPhysicalIdentity(Player player, CallbackInfo ci) {
    if (!(Create.RAILWAYS.sided(player == null ? null : player.level()).trains.get(id)
        instanceof PhysicalTrainProxy)) return;
    if (player != null && !player.level().isClientSide)
      player.displayClientMessage(
          Component.translatable("gui.guidedrailways.station.controls_name"), true);
    ci.cancel();
  }
}
