package com.physicalrailways.mixin;

import com.physicalrailways.LagProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VertexBuffer.class)
public abstract class LagProbeVertexMixin {
  @WrapMethod(method = "bind")
  private void physicalrailways$bind(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.VBO_BIND, start); }
  }
  @WrapMethod(method = "upload")
  private void physicalrailways$upload(MeshData mesh, Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(mesh); } finally { LagProbe.end(LagProbe.Stage.VBO_UPLOAD, start); }
  }
  @WrapMethod(method = "close")
  private void physicalrailways$close(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.VBO_CLOSE, start); }
  }
}
