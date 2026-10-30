package com.guidedrailways.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Reuse this shader's linked sampler locations instead of querying the driver on every draw. */
@Mixin(ShaderInstance.class)
public abstract class ShaderSamplerLocationMixin {
  @Shadow @Final private List<String> samplerNames;
  @Shadow @Final private List<Integer> samplerLocations;
  @Shadow @Final private int programId;

  @Unique
  private static final boolean guidedrailways$disableSamplerReuse =
      Boolean.getBoolean("guidedrailways.disableSamplerLocationReuse");

  @WrapOperation(
      method = "apply",
      at = @At(value = "INVOKE", target =
          "Lcom/mojang/blaze3d/shaders/Uniform;glGetUniformLocation(ILjava/lang/CharSequence;)I"),
      require = 0)
  private int guidedrailways$samplerLocation(int program, CharSequence name, Operation<Integer> original) {
    // No extra cache: updateLocations and resource reload continue to own location lifetime.
    if (!guidedrailways$disableSamplerReuse && program == programId
        && samplerNames != null && samplerLocations != null
        && samplerNames.size() == samplerLocations.size()) {
      int index = samplerNames.indexOf(name instanceof String ? name : name.toString());
      if (index >= 0) {
        Integer location = samplerLocations.get(index);
        if (location != null) return location;
      }
    }
    // Preserve other wrappers and vanilla behavior for any nonstandard/unfinished shader state.
    return original.call(program, name);
  }
}
