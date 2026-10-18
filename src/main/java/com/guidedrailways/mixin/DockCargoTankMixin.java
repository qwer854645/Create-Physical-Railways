package com.guidedrailways.mixin;
import com.guidedrailways.CargoTransfers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorTank",remap=false)
public abstract class DockCargoTankMixin {
    @Inject(method={"insert","extract"},at=@At("RETURN"))
    private void guidedrailways$transfer(@Coerce Object fluid,long amount,boolean simulate,Runnable callback,CallbackInfoReturnable<Long> cir){CargoTransfers.exchanged(this,simulate,cir.getReturnValue());}
}
