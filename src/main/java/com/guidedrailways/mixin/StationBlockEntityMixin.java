package com.guidedrailways.mixin;

import com.guidedrailways.StationPresence;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationBlockEntityMixin {
  @Inject(method = "applyAutoSchedule", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$keepScheduleInControls(CallbackInfo ci) {
    var station = ((StationBlockEntity) (Object) this).getStation();
    if (station != null
        && station.getImminentTrain() instanceof com.guidedrailways.PhysicalTrainProxy) ci.cancel();
  }

  @Inject(method = "dropSchedule", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$physicalScheduleHint(
      ServerPlayer player, com.simibubi.create.content.trains.entity.Train train, CallbackInfo ci) {
    if (!(train instanceof com.guidedrailways.PhysicalTrainProxy)) return;
    if (player != null)
      player.displayClientMessage(
          Component.translatable("gui.guidedrailways.auto.physical_station"), true);
    ci.cancel();
  }

  @Inject(method = "enterAssemblyMode", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$physicalAssemblyHint(
      ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
    var self = (StationBlockEntity) (Object) this;
    if (StationPresence.present(self.getLevel(), self.getBlockPos())) {
      player.displayClientMessage(
          Component.translatable("gui.guidedrailways.auto.physical_station"), true);
      cir.setReturnValue(false);
    }
  }

  @Inject(method = "tryEnterAssemblyMode", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$preventPhysicalAssembly(CallbackInfoReturnable<Boolean> cir) {
    var self = (StationBlockEntity) (Object) this;
    if (StationPresence.present(self.getLevel(), self.getBlockPos())) cir.setReturnValue(false);
  }
}
