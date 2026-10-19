package com.guidedrailways.mixin;
import com.guidedrailways.RailwayCoupling;
import com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity",remap=false)
public abstract class RailwayCouplerMixin {
    @Unique private int guidedrailways$mask=-1;
    @Unique private int guidedrailways$mode;
    @Unique private TrackCouplerBlockEntity guidedrailways$tile(){return (TrackCouplerBlockEntity)(Object)this;}
    @Inject(method="tick",at=@At("HEAD"))
    private void guidedrailways$refresh(CallbackInfo ci){
        var tile=guidedrailways$tile();if(tile.getLevel()==null||tile.getLevel().isClientSide)return;
        var status=RailwayCoupling.inspect(tile,false);
        if(status.mask()!=guidedrailways$mask||status.mode()!=guidedrailways$mode){guidedrailways$mask=status.mask();guidedrailways$mode=status.mode();tile.notifyUpdate();}
    }
    @Inject(method="onPowered",at=@At("HEAD"),cancellable=true)
    private void guidedrailways$operate(CallbackInfo ci){
        if(guidedrailways$mask<0)return;
        ci.cancel();try{RailwayCoupling.inspect(guidedrailways$tile(),true);}
        catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger("guidedrailways").error("Trackside physical coupling rejected at {}",guidedrailways$tile().getBlockPos(),error);}
    }
    @Inject(method="getOperationMode",at=@At("HEAD"),cancellable=true)
    private void guidedrailways$mode(CallbackInfoReturnable<TrackCouplerBlockEntity.OperationMode> cir){
        if(guidedrailways$mask>=0)cir.setReturnValue(switch(guidedrailways$mode){case 1->TrackCouplerBlockEntity.OperationMode.COUPLING;case 2->TrackCouplerBlockEntity.OperationMode.DECOUPLING;default->TrackCouplerBlockEntity.OperationMode.NONE;});
    }
    @Inject(method="getTargetAnalogOutput",at=@At("HEAD"),cancellable=true)
    private void guidedrailways$output(CallbackInfoReturnable<Integer> cir){if(guidedrailways$mask>=0)cir.setReturnValue(guidedrailways$mask+(guidedrailways$mode==1?8:guidedrailways$mode==2?4:0));}
    @Inject(method="write",at=@At("TAIL"))
    private void guidedrailways$write(CompoundTag tag,HolderLookup.Provider provider,boolean client,CallbackInfo ci){if(client){tag.putInt("GuidedPhysicalMask",guidedrailways$mask);tag.putInt("GuidedPhysicalMode",guidedrailways$mode);}}
    @Inject(method="read",at=@At("TAIL"))
    private void guidedrailways$read(CompoundTag tag,HolderLookup.Provider provider,boolean client,CallbackInfo ci){guidedrailways$mask=client&&tag.contains("GuidedPhysicalMask")?tag.getInt("GuidedPhysicalMask"):-1;guidedrailways$mode=client?tag.getInt("GuidedPhysicalMode"):0;}
    @Inject(method="addToGoggleTooltip",at=@At("HEAD"),cancellable=true)
    private void guidedrailways$goggles(List<Component> tooltip,boolean sneaking,CallbackInfoReturnable<Boolean> cir){
        if(guidedrailways$mask<0)return;
        tooltip.add(Component.translatable("message.guidedrailways.rail_coupler."+(guidedrailways$mode==1?"couple":guidedrailways$mode==2?"decouple":"align")));cir.setReturnValue(true);
    }
}
