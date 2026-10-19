package com.guidedrailways.mixin;
import com.guidedrailways.NativeTrainContacts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe the existing contact stream; do not consume or replace Sable's collisions. */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",remap=false)
public abstract class NativeTrainContactMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Object2ObjectMap<KinematicContraption,Object> activeContraptions;
    @Unique private NativeTrainContacts.Frame guidedrailways$contacts;
    @Unique private boolean guidedrailways$reported;
    @Inject(method="prePhysicsTicks",at=@At("HEAD"))
    private void guidedrailways$snapshot(CallbackInfo ci){
        if(!guidedrailways$reported){org.slf4j.LoggerFactory.getLogger("guidedrailways").info("[NativeTrainContact] Rapier contact feedback bridge active");guidedrailways$reported=true;}
        var indexed=new java.util.HashMap<Integer,KinematicContraption>();
        for(var entry:activeContraptions.entrySet())indexed.put(((TrackedContraptionAccessor)entry.getValue()).guidedrailways$runtimeId(),entry.getKey());
        guidedrailways$contacts=NativeTrainContacts.capture(level,indexed);
    }
    @ModifyExpressionValue(method="processCollisionEffects",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(J)[D"))
    private double[] guidedrailways$feedback(double[] contacts){
        if(guidedrailways$contacts!=null){guidedrailways$contacts.accept(contacts);guidedrailways$contacts=null;}
        return contacts;
    }
}
