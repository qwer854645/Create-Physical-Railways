package com.guidedrailways.mixin;

import com.guidedrailways.TrackTopology;
import com.simibubi.create.content.trains.graph.TrackGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = TrackGraph.class, remap = false)
public abstract class TrackTopologyMixin {
  @Inject(
      method = {"addNode", "disconnectNodes", "transfer", "transferAll", "setId"},
      at = @At("RETURN"))
  private void guidedrailways$changed(CallbackInfo ci) {
    TrackTopology.changed();
  }

  // putConnection may replace an existing edge while returning false; every completed write attempt
  // invalidates.
  @Inject(
      method = {"removeNode", "putConnection", "addNodeIfAbsent"},
      at = @At("RETURN"))
  private void guidedrailways$changedResult(CallbackInfoReturnable<Boolean> ci) {
    TrackTopology.changed();
  }
}
