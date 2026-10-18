package com.guidedrailways.mixin;
import com.guidedrailways.StationPresence;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.objectweb.asm.Opcodes;

@Mixin(value=StationBlockEntity.class,remap=false)
public abstract class StationBlockEntityMixin {
    @Shadow boolean trainPresent;
    @Unique private boolean guidedrailways$present;
    @Inject(method="write",at=@At("RETURN"),require=1)
    private void guidedrailways$writePresence(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        var self=(StationBlockEntity)(Object)this;
        tag.putBoolean("GuidedPhysicalPresent",self.getLevel()!=null&&self.getLevel().isClientSide?guidedrailways$present:StationPresence.present(self.getLevel(),self.getBlockPos()));
    }
    @Inject(method="read",at=@At("RETURN"),require=1)
    private void guidedrailways$readPresence(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){guidedrailways$present=tag.getBoolean("GuidedPhysicalPresent");}
    @Redirect(method="tick",at=@At(value="FIELD",target="Lcom/simibubi/create/content/trains/station/StationBlockEntity;trainPresent:Z",opcode=Opcodes.GETFIELD),require=1)
    private boolean guidedrailways$clientFlag(StationBlockEntity station){
        // Only client flag animation sees the combined state. Never claim a native Train exists.
        return trainPresent||station.getLevel()!=null&&station.getLevel().isClientSide&&guidedrailways$present;
    }
    @Inject(method="enterAssemblyMode",at=@At("HEAD"),cancellable=true,require=1)
    private void guidedrailways$physicalAssemblyHint(ServerPlayer player,CallbackInfoReturnable<Boolean> cir){
        var self=(StationBlockEntity)(Object)this;
        if(StationPresence.present(self.getLevel(),self.getBlockPos())){player.displayClientMessage(Component.translatable("gui.guidedrailways.auto.physical_station"),true);cir.setReturnValue(false);}
    }
    @Inject(method="tryEnterAssemblyMode",at=@At("HEAD"),cancellable=true,require=1)
    private void guidedrailways$preventPhysicalAssembly(CallbackInfoReturnable<Boolean> cir){
        var self=(StationBlockEntity)(Object)this;if(StationPresence.present(self.getLevel(),self.getBlockPos()))cir.setReturnValue(false);
    }
}
