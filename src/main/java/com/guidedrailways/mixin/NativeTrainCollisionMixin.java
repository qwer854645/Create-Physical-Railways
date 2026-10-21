package com.guidedrailways.mixin;

import com.guidedrailways.NativeTrainBridge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.trains.entity.Train;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Proxy transport/storage/collision belong to the physical backend, not native carriage machinery.
 */
@Mixin(targets = "com.simibubi.create.content.trains.entity.Train", remap = false)
public abstract class NativeTrainCollisionMixin {
  @ModifyExpressionValue(
      method = "findCollidingTrain",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lcom/simibubi/create/content/trains/GlobalRailwayManager;trains:Ljava/util/Map;",
              opcode = Opcodes.GETFIELD),
      require = 1)
  private Map<java.util.UUID, Train> guidedrailways$nativeOnly(Map<java.util.UUID, Train> trains) {
    return NativeTrainBridge.nativeOnly(trains);
  }
}
