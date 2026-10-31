package com.guidedrailways.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Veil registers its first-person framebuffer each frame, including an expensive driver debug label. */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.framebuffer.FramebufferManager", remap = false)
public abstract class VeilFramebufferRegistrationMixin {
  @Shadow @Final private Map<ResourceLocation, Object> framebuffers;
  @Shadow @Final private Set<ResourceLocation> manualFramebuffers;

  @Unique
  private static final boolean guidedrailways$disableFramebufferReuse =
      Boolean.getBoolean("guidedrailways.disableFramebufferRegistrationReuse");

  @Inject(method = "setFramebuffer(Lnet/minecraft/resources/ResourceLocation;Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;)V",
      at = @At("HEAD"), cancellable = true, require = 0)
  private void guidedrailways$reuseRegistration(ResourceLocation name, @Coerce Object framebuffer, CallbackInfo ci) {
    // Use Veil's live registry as the only source of truth; no ID cache or extra lifecycle state.
    if (!guidedrailways$disableFramebufferReuse && framebuffer != null
        && manualFramebuffers.contains(name) && framebuffers.get(name) == framebuffer)
      ci.cancel();
  }
}
