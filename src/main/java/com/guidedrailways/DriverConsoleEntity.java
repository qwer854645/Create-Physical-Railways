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
    boolean active;int direction=1,setting=4,brake=1;
    double actual,available;int supports,powered,guided,mountBrakes;
    DriverConsoleEntity(BlockPos p,BlockState s){super(Content.DRIVER_ENTITY.get(),p,s);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server)DriverControls.register(server,this);}
    @Override public Component getDisplayName(){return Component.translatable("block.guidedrailways.driver_console");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new DriverMenu(id,inventory,this);}
    void command(Player player,int command){
        if(!(level instanceof ServerLevel server)||!player.mayBuild()||!server.mayInteract(player,worldPosition))return;
        switch(command){
            case 0 -> {if(!active&&DriverControls.body(this)==null){player.displayClientMessage(Component.translatable("gui.guidedrailways.driver.install"),true);return;}if(active){active=false;brake=1;}else{DriverControls.claim(server,this);active=true;brake=1;}}
            case 1 -> setting=Math.max(0,setting-1);
            case 2 -> setting=Math.min(DriverModel.MAX_SPEED,setting+1);
            case 3 -> direction=1;
            case 4 -> direction=0;
            case 5 -> direction=-1;
            case 6 -> {if(active){brake=0;VehiclePhysics.releaseMountBrakes(this);}}
            case 7 -> brake=1;
            case 8 -> DriverControls.emergency(server,this);
            default -> {return;}
        }
        setChanged();
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[DriverConsole] pos={} operator={} active={} direction={} target={} brake={}",worldPosition,player.getUUID(),active,direction,setting,brake);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("TargetSpeed",setting);tag.putInt("Direction",direction);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);setting=tag.contains("TargetSpeed")?Math.clamp(tag.getInt("TargetSpeed"),0,DriverModel.MAX_SPEED):4;direction=tag.contains("Direction")?Math.clamp(tag.getInt("Direction"),-1,1):1;
        // Reloading/assembling never restarts an unattended train.
        active=false;brake=1;
    }
    @Override public void setRemoved(){if(level instanceof ServerLevel server)DriverControls.removed(server,this);super.setRemoved();}
}
