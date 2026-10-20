package com.guidedrailways.mixin;

import com.guidedrailways.LogisticsCargoBridge;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSnapshot",
    remap = false)
public abstract class LogisticsSnapshotMixin {
  @ModifyVariable(method = "capture", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  private static List<?> guidedrailways$consist(List<?> entries, Level level, List<?> original) {
    return LogisticsCargoBridge.expand(level, entries);
  }

  @Inject(method = "capture", at = @At("HEAD"), cancellable = true)
  private static void guidedrailways$unloaded(
      Level level, List<?> entries, CallbackInfoReturnable<Object> cir) {
    if (LogisticsCargoBridge.incomplete(level, entries))
      try {
        cir.setReturnValue(
            LogisticsCargoBridge.emptySnapshot(
                Class.forName(
                    "net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSnapshot")));
      } catch (ClassNotFoundException error) {
        throw new IllegalStateException(error);
      }
  }
}
