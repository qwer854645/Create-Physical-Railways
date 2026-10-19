package com.guidedrailways.mixin;
import com.guidedrailways.CouplerEndpoint;
import com.guidedrailways.RailwayHardware;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.railwayteam.railways.content.buffer.DyeableBlockEntity","com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlockEntity"},remap=false)
public abstract class RailwayHardwareEntityMixin extends SmartBlockEntity implements CouplerEndpoint.Provider {
    @Unique private CouplerEndpoint guidedrailways$coupler;
    protected RailwayHardwareEntityMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public CouplerEndpoint guidedrailways$endpoint(){
        if(!RailwayHardware.coupler(getBlockState()))return null;
        if(guidedrailways$coupler==null)guidedrailways$coupler=new CouplerEndpoint(this);
        return guidedrailways$coupler;
    }
    @Override public void tick(){
        super.tick();var endpoint=guidedrailways$endpoint();
        if(endpoint!=null)endpoint.tick();
        else if(guidedrailways$coupler!=null){guidedrailways$coupler.destroyed();guidedrailways$coupler=null;}
    }
    @Override public void invalidate(){if(guidedrailways$coupler!=null)guidedrailways$coupler.unloaded();super.invalidate();}
    @Override public void destroy(){if(guidedrailways$coupler!=null)guidedrailways$coupler.destroyed();super.destroy();}
    @Inject(method="write",at=@At("TAIL"))
    private void guidedrailways$write(CompoundTag tag,HolderLookup.Provider registries,boolean client,CallbackInfo ci){
        if(guidedrailways$coupler!=null)tag.put("GuidedCoupler",guidedrailways$coupler.save());
    }
    @Inject(method="read",at=@At("TAIL"))
    private void guidedrailways$read(CompoundTag tag,HolderLookup.Provider registries,boolean client,CallbackInfo ci){
        var endpoint=guidedrailways$endpoint();if(endpoint!=null&&tag.contains("GuidedCoupler",10))endpoint.read(tag.getCompound("GuidedCoupler"));
    }
}
