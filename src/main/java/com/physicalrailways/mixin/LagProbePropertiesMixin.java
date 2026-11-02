package com.physicalrailways.mixin;

import com.physicalrailways.LagProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;

/** Measure execution on the render thread, not merely enqueueing the network task. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket", remap = false)
public abstract class LagProbePropertiesMixin {
  @WrapMethod(method = "lambda$handle$0")
  private void physicalrailways$properties(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.PHYSICS_PROPERTY_SYNC, start); }
  }
}
