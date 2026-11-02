package com.physicalrailways.mixin;

import com.physicalrailways.LagProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SectionRenderDispatcher.class)
public abstract class LagProbeUploadsMixin {
  @WrapMethod(method = "uploadAllPendingUploads")
  private void physicalrailways$uploads(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.CHUNK_UPLOAD_BATCH, start); }
  }
}
