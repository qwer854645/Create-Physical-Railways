package com.physicalrailways.mixin;

import com.physicalrailways.PhysicalTrainProxy;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.station.TrainEditPacket;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persist permitted native edits through the physical controller before broadcasting them. */
@Mixin(value = TrainEditPacket.class, remap = false)
public abstract class PhysicalTrainEditMixin {
  @Shadow @Final protected UUID id;
  @Shadow @Final protected String name;
  @Shadow @Final protected net.minecraft.resources.ResourceLocation iconType;
  @Shadow @Final protected int mapColor;

  @Inject(method = "handleSided", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$keepPhysicalIdentity(Player player, CallbackInfo ci) {
    if (!(Create.RAILWAYS.sided(player == null ? null : player.level()).trains.get(id)
        instanceof PhysicalTrainProxy proxy)) return;
    if (player == null || player.level().isClientSide) return;
    if (proxy.editIdentity(player, name, iconType, mapColor))
      net.createmod.catnip.platform.CatnipServices.NETWORK.sendToAllClients(
          new TrainEditPacket.TrainEditReturnPacket(
              id, proxy.name.getString(), proxy.icon.getId(), proxy.mapColorIndex));
    ci.cancel();
  }
}
