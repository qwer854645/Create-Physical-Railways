package com.physicalrailways.mixin;

import com.physicalrailways.LagProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public abstract class LagProbeWorldMixin {
  @WrapMethod(method = "allChanged")
  private void physicalrailways$rebuild(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.WORLD_REBUILD, start); }
  }
}
