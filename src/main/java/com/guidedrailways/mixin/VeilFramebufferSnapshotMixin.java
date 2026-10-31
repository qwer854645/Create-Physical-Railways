package com.guidedrailways.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** GL_FRAMEBUFFER_BINDING aliases GL_DRAW_FRAMEBUFFER_BINDING; query once per snapshot. */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.framebuffer.FramebufferStack", remap = false)
public abstract class VeilFramebufferSnapshotMixin {
  @Unique
  private static final boolean guidedrailways$disableSnapshotReuse =
      Boolean.getBoolean("guidedrailways.disableFramebufferSnapshotReuse");

  @WrapOperation(method = "push", at = @At(value = "INVOKE",
      target = "Lorg/lwjgl/opengl/GL30C;glGetInteger(I)I"), require = 0)
  private static int guidedrailways$snapshotBinding(int query, Operation<Integer> original,
      @Share("guidedrailways$drawBinding") LocalIntRef drawBinding,
      @Share("guidedrailways$haveDrawBinding") LocalBooleanRef haveDrawBinding) {
    if (!guidedrailways$disableSnapshotReuse && query == GL30C.GL_DRAW_FRAMEBUFFER_BINDING) {
      if (haveDrawBinding.get()) return drawBinding.get();
      int value = original.call(query);
      drawBinding.set(value);
      haveDrawBinding.set(true);
      return value;
    }
    return original.call(query);
  }
}
