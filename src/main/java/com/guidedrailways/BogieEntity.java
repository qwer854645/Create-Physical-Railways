package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
public final class BogieEntity extends SmartBlockEntity {
    public double distance,speed;
    public BogieEntity(BlockPos p,BlockState s){super(Content.BOGIE_ENTITY.get(),p,s);}
    @Override public void addBehaviours(java.util.List<BlockEntityBehaviour> list){}
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean client){super.write(tag,registries,client);tag.putDouble("WheelDistance",distance);tag.putDouble("LinearSpeed",speed);}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean client){super.read(tag,registries,client);distance=tag.getDouble("WheelDistance");speed=tag.getDouble("LinearSpeed");}
}
