package com.guidedrailways.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(
    targets =
        "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline$TrackedKinematicContraption",
    remap = false)
public interface TrackedContraptionAccessor {
  @Accessor("id")
  int guidedrailways$runtimeId();
}
