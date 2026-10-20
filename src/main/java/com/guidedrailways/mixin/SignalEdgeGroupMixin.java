package com.guidedrailways.mixin;

import com.guidedrailways.PhysicalSignals;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.*;
import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Add external occupancy without inserting synthetic Train objects into Create's collections. */
@Mixin(value = SignalEdgeGroup.class, remap = false)
public abstract class SignalEdgeGroupMixin {
  @Shadow public UUID id;

  @Inject(
      method = "isThisOccupiedUnless(Lcom/simibubi/create/content/trains/entity/Train;)Z",
      at = @At("HEAD"),
      cancellable = true,
      require = 1)
  private void guidedrailways$train(Train train, CallbackInfoReturnable<Boolean> cir) {
    if (PhysicalSignals.nativeBlocked(id, null)) cir.setReturnValue(true);
  }

  @Inject(
      method = "isThisOccupiedUnless(Lcom/simibubi/create/content/trains/signal/SignalBoundary;)Z",
      at = @At("HEAD"),
      cancellable = true,
      require = 1)
  private void guidedrailways$signal(SignalBoundary boundary, CallbackInfoReturnable<Boolean> cir) {
    if (PhysicalSignals.nativeBlocked(id, boundary == null ? null : boundary.id))
      cir.setReturnValue(true);
  }
}
