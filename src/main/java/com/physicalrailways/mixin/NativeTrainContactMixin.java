package com.physicalrailways.mixin;

import com.physicalrailways.NativeTrainContacts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the existing contact stream; do not consume or replace Sable's collisions. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class NativeTrainContactMixin {
  @Shadow @Final private ServerLevel level;
  @Shadow @Final private Object2ObjectMap<KinematicContraption, Object> activeContraptions;
  @Unique private NativeTrainContacts.Frame physicalrailways$contacts;
  @Unique private boolean physicalrailways$reported;

  @Inject(method = "prePhysicsTicks", at = @At("HEAD"))
  private void physicalrailways$snapshot(CallbackInfo ci) {
    if (!physicalrailways$reported) {
      org.slf4j.LoggerFactory.getLogger("physicalrailways")
          .info("[NativeTrainContact] Rapier contact feedback bridge active");
      physicalrailways$reported = true;
    }
    var indexed = new java.util.HashMap<Integer, KinematicContraption>();
    for (var entry : activeContraptions.entrySet())
      indexed.put(
          ((TrackedContraptionAccessor) entry.getValue()).physicalrailways$runtimeId(),
          entry.getKey());
    physicalrailways$contacts = NativeTrainContacts.capture(level, indexed);
  }

  @ModifyExpressionValue(
      method = "processCollisionEffects",
      at =
          @At(
              value = "INVOKE",
              target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(J)[D"))
  private double[] physicalrailways$feedback(double[] contacts) {
    if (physicalrailways$contacts != null) {
      physicalrailways$contacts.accept(contacts);
      physicalrailways$contacts = null;
    }
    return contacts;
  }
}
