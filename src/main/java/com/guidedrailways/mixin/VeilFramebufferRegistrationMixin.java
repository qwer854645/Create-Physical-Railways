package com.guidedrailways.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Preserve registration and other hooks; suppress only a redundant debug-label call. */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.framebuffer.FramebufferManager", remap = false)
public abstract class VeilFramebufferRegistrationMixin {
  @Unique
  private static final boolean guidedrailways$disableFramebufferReuse =
      Boolean.getBoolean("guidedrailways.disableFramebufferRegistrationReuse");

  @WrapOperation(method = "setFramebuffer", at = @At(value = "INVOKE",
      target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
  private Object guidedrailways$observeRegistration(Map<Object, Object> registry, Object name, Object framebuffer,
      Operation<Object> original, @Share("guidedrailways$repeatRegistration") LocalBooleanRef repeated) {
    Object previous = original.call(registry, name, framebuffer);
    repeated.set(framebuffer != null && previous == framebuffer);
    return previous;
  }

  @WrapOperation(method = "setFramebuffer", at = @At(value = "INVOKE",
      target = "Lfoundry/veil/api/client/render/ext/VeilDebug;objectLabel(IILjava/lang/CharSequence;)V"), require = 0)
  private void guidedrailways$label(@Coerce Object debug, int type, int id, CharSequence label,
      Operation<Void> original, @Share("guidedrailways$repeatRegistration") LocalBooleanRef repeated) {
    if (guidedrailways$disableFramebufferReuse || !repeated.get()) original.call(debug, type, id, label);
  }
}
