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
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox(){
        double x0=0,y0=0,z0=0,x1=1,y1=1,z1=1;
        for(var b:BogieCollision.boxes(BogieBlock.gauge(getBlockState()))){x0=Math.min(x0,b.x0());y0=Math.min(y0,b.y0());z0=Math.min(z0,b.z0());x1=Math.max(x1,b.x1());y1=Math.max(y1,b.y1());z1=Math.max(z1,b.z1());}
        return new net.minecraft.world.phys.AABB(x0,y0,z0,x1,y1,z1).inflate(1.0/16).move(worldPosition);
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean client){super.write(tag,registries,client);tag.putDouble("WheelDistance",distance);tag.putDouble("LinearSpeed",speed);}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean client){super.read(tag,registries,client);distance=tag.getDouble("WheelDistance");speed=tag.getDouble("LinearSpeed");}
}
