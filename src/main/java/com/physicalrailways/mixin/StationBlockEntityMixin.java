package com.physicalrailways.mixin;

import com.physicalrailways.StationPresence;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationBlockEntityMixin {
  @Inject(method = "applyAutoSchedule", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$keepScheduleInControls(CallbackInfo ci) {
    var station = ((StationBlockEntity) (Object) this).getStation();
    if (station != null
        && station.getImminentTrain() instanceof com.physicalrailways.PhysicalTrainProxy) ci.cancel();
  }

  @Inject(method = "dropSchedule", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$physicalScheduleHint(
      ServerPlayer player, com.simibubi.create.content.trains.entity.Train train, CallbackInfo ci) {
    if (!(train instanceof com.physicalrailways.PhysicalTrainProxy)) return;
    if (player != null)
      player.displayClientMessage(
          Component.translatable("gui.physicalrailways.auto.physical_station"), true);
    ci.cancel();
  }

  @Inject(method = "enterAssemblyMode", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$physicalAssemblyHint(
      ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
    var self = (StationBlockEntity) (Object) this;
    if (StationPresence.blocksAssembly(self)) {
      if (player != null)
        player.displayClientMessage(
            Component.translatable("gui.physicalrailways.auto.physical_station"), true);
      cir.setReturnValue(false);
    }
  }

  @Inject(method = "tryEnterAssemblyMode", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$preventPhysicalAssembly(CallbackInfoReturnable<Boolean> cir) {
    var self = (StationBlockEntity) (Object) this;
    if (StationPresence.blocksAssembly(self)) cir.setReturnValue(false);
  }

  @Inject(method = "tryDisassembleTrain", at = @At("HEAD"), cancellable = true, require = 1)
  private void physicalrailways$preventPhysicalDisassembly(
      ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
    var self = (StationBlockEntity) (Object) this;
    if (!StationPresence.blocksAssembly(self)) return;
    if (player != null)
      player.displayClientMessage(
          Component.translatable("gui.physicalrailways.auto.physical_station"), true);
    cir.setReturnValue(false);
  }
}
