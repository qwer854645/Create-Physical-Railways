package com.guidedrailways.mixin;

import com.guidedrailways.LagProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets = {"dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData",
    "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData"}, remap = false)
public abstract class LagProbeSableReleaseMixin {
  @WrapMethod(method = "close")
  private void guidedrailways$release(Operation<Void> original) {
    long start = LagProbe.begin();
    try { original.call(); } finally { LagProbe.end(LagProbe.Stage.SABLE_RENDER_RELEASE, start); }
  }
}
