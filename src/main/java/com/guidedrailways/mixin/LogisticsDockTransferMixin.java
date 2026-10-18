package com.guidedrailways.mixin;
import com.guidedrailways.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot",remap=false)
public abstract class LogisticsDockTransferMixin {
    @Inject(method="captureConnector",at=@At("RETURN"),cancellable=true)
    private static void guidedrailways$activity(@Coerce Object connector,CallbackInfoReturnable<Object> cir){cir.setReturnValue(LogisticsCargoBridge.stamp((BlockEntity)connector,cir.getReturnValue()));}
    @Inject(method="handoffLocalItem",at=@At("RETURN"))
    private static void guidedrailways$handoff(@Coerce Object source,@Coerce Object destination,CallbackInfoReturnable<Integer> cir){if(cir.getReturnValue()>0){CargoTransfers.changed((BlockEntity)source);CargoTransfers.changed((BlockEntity)destination);}}
}
