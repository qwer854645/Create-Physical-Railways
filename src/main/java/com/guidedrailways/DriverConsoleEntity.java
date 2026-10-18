package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class DriverConsoleEntity extends BlockEntity implements MenuProvider {
    java.util.UUID operator;int steer;long steerAt;
    boolean active,emergency;int direction=1,notch=-4;
    float lever=-1,previousLever=-1,reverser=1,previousReverser=1;
    double actual,available;int supports,powered,guided,mountBrakes;
    DriverConsoleEntity(BlockPos p,BlockState s){super(Content.DRIVER_ENTITY.get(),p,s);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server)DriverControls.register(server,this);}
    @Override public Component getDisplayName(){return Component.translatable("block.guidedrailways.driver_console");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new DriverMenu(id,inventory,this);}
    void clientTick(){previousLever=lever;lever+=(notch/4f-lever)*.5f;previousReverser=reverser;reverser+=(direction-reverser)*.5f;}
    void updateVisual(){
        setChanged();if(level!=null&&!level.isClientSide&&!isRemoved()){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);DriverNetwork.sync(this,active);}
    }
    void park(){active=false;steer=0;notch=-4;DriverNetwork.sync(this,false);operator=null;updateVisual();}
    void command(Player player,int command){
        if(!(level instanceof ServerLevel server)||!player.mayBuild()||!server.mayInteract(player,worldPosition))return;
        if(active&&operator!=null&&!operator.equals(player.getUUID())&&command!=8){hint(player,"busy");return;}
        if(command==19){if(operator!=null&&operator.equals(player.getUUID()))park();return;}
        if(command>=20&&command<=22){if(active&&player.getUUID().equals(operator)){steer=command==20?-1:command==21?1:0;steerAt=server.getGameTime();}return;}
        int oldNotch=notch,oldDirection=direction;boolean oldActive=active,oldEmergency=emergency;
        if(command==0){
            if(!active&&DriverControls.body(this)==null){hint(player,"install");return;}
            if(active)park();else{DriverControls.claim(server,this,player);active=true;operator=player.getUUID();notch=-4;}
        }else if(command==8){DriverControls.emergency(server,this);}
        else{
            if(!active)return;
            if(command==9){
                if(!DriverControls.canChangeDirection(this)){hint(player,"reset_stop");return;}
                DriverControls.resetEmergency(server,this);
            }else if(emergency){hint(player,"reset_first");return;}
            else if(command>=3&&command<=5){
                int next=command==3?1:command==4?0:-1;
                if(next!=direction){if(!DriverControls.canChangeDirection(this)){hint(player,"reverser_stop");return;}direction=next;}
            }else if(command==1)notch=DriverModel.step(notch,-1);
            else if(command==2)notch=DriverModel.step(notch,1);
            else if(command==6)notch=0;
            else if(command==7)notch=-4;
            else if(command>=10&&command<=18)notch=command-14;
            else return;
            if(notch>=0)VehiclePhysics.releaseMountBrakes(this);
        }
        if(oldNotch==notch&&oldDirection==direction&&oldActive==active&&oldEmergency==emergency)return;
        updateVisual();
        var pos=DriverControls.position(this);
        server.playSound(null,pos.x,pos.y,pos.z,net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,.45f,1+notch*.035f);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[DriverConsole] pos={} operator={} active={} direction={} notch={} emergency={}",worldPosition,player.getUUID(),active,direction,notch,emergency);
    }
    void open(ServerPlayer player){player.openMenu(this);}
    private void hint(Player player,String key){player.displayClientMessage(Component.translatable("gui.guidedrailways.driver."+key),true);}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=saveWithoutMetadata(registries);tag.putBoolean("VisualActive",active);tag.putInt("VisualNotch",notch);tag.putBoolean("VisualEmergency",emergency);return tag;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("Direction",direction);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);direction=tag.contains("Direction")?Math.clamp(tag.getInt("Direction"),-1,1):1;
        if(tag.contains("VisualNotch")){active=tag.getBoolean("VisualActive");notch=Math.clamp(tag.getInt("VisualNotch"),-4,4);emergency=tag.getBoolean("VisualEmergency");}
        else{active=false;notch=-4;emergency=false;} // Never resume traction when loading/assembling.
    }
    @Override public void setRemoved(){if(level instanceof ServerLevel server)DriverControls.removed(server,this);super.setRemoved();}
}
