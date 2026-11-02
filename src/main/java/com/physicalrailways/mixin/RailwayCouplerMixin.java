package com.physicalrailways.mixin;

import com.physicalrailways.RailwayCoupling;
import com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(
    targets = "com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity",
    remap = false)
public abstract class RailwayCouplerMixin {
  @Unique private int physicalrailways$mask = -1;
  @Unique private int physicalrailways$mode;

  @Unique
  private TrackCouplerBlockEntity physicalrailways$tile() {
    return (TrackCouplerBlockEntity) (Object) this;
  }

  @Inject(method = "tick", at = @At("HEAD"))
  private void physicalrailways$refresh(CallbackInfo ci) {
    var tile = physicalrailways$tile();
    if (tile.getLevel() == null || tile.getLevel().isClientSide) return;
    var status = RailwayCoupling.inspect(tile, false);
    if (status.mask() != physicalrailways$mask || status.mode() != physicalrailways$mode) {
      physicalrailways$mask = status.mask();
      physicalrailways$mode = status.mode();
      tile.notifyUpdate();
    }
  }

  @Inject(method = "onPowered", at = @At("HEAD"), cancellable = true)
  private void physicalrailways$operate(CallbackInfo ci) {
    if (physicalrailways$mask < 0) return;
    ci.cancel();
    try {
      RailwayCoupling.inspect(physicalrailways$tile(), true);
    } catch (RuntimeException error) {
      org.slf4j.LoggerFactory.getLogger("physicalrailways")
          .error(
              "Trackside physical coupling rejected at {}",
              physicalrailways$tile().getBlockPos(),
              error);
    }
  }

  @Inject(method = "getOperationMode", at = @At("HEAD"), cancellable = true)
  private void physicalrailways$mode(
      CallbackInfoReturnable<TrackCouplerBlockEntity.OperationMode> cir) {
    if (physicalrailways$mask >= 0)
      cir.setReturnValue(
          switch (physicalrailways$mode) {
            case 1 -> TrackCouplerBlockEntity.OperationMode.COUPLING;
            case 2 -> TrackCouplerBlockEntity.OperationMode.DECOUPLING;
            default -> TrackCouplerBlockEntity.OperationMode.NONE;
          });
  }

  @Inject(method = "getTargetAnalogOutput", at = @At("HEAD"), cancellable = true)
  private void physicalrailways$output(CallbackInfoReturnable<Integer> cir) {
    if (physicalrailways$mask >= 0)
      cir.setReturnValue(
          physicalrailways$mask + (physicalrailways$mode == 1 ? 8 : physicalrailways$mode == 2 ? 4 : 0));
  }

  @Inject(method = "write", at = @At("TAIL"))
  private void physicalrailways$write(
      CompoundTag tag, HolderLookup.Provider provider, boolean client, CallbackInfo ci) {
    if (client) {
      tag.putInt("GuidedPhysicalMask", physicalrailways$mask);
      tag.putInt("GuidedPhysicalMode", physicalrailways$mode);
    }
  }

  @Inject(method = "read", at = @At("TAIL"))
  private void physicalrailways$read(
      CompoundTag tag, HolderLookup.Provider provider, boolean client, CallbackInfo ci) {
    physicalrailways$mask =
        client && tag.contains("GuidedPhysicalMask") ? tag.getInt("GuidedPhysicalMask") : -1;
    physicalrailways$mode = client ? tag.getInt("GuidedPhysicalMode") : 0;
  }

  @Inject(method = "addToGoggleTooltip", at = @At("HEAD"), cancellable = true)
  private void physicalrailways$goggles(
      List<Component> tooltip, boolean sneaking, CallbackInfoReturnable<Boolean> cir) {
    if (physicalrailways$mask < 0) return;
    com.physicalrailways.GoggleText.add(tooltip,
        Component.translatable(
            "message.physicalrailways.rail_coupler."
                + (physicalrailways$mode == 1
                    ? "couple"
                    : physicalrailways$mode == 2 ? "decouple" : "align")));
    cir.setReturnValue(true);
  }
}
