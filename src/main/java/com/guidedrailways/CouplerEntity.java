package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.UUID;
public final class CouplerEntity extends BlockEntity {
    UUID endpointId=UUID.randomUUID();
    Vector3d anchor=new Vector3d(.5,.5,.5);
    Quaterniond frame=new Quaterniond();
    boolean hasGeometry;
    CouplerEntity(BlockPos pos,BlockState state){super(Content.COUPLER_ENTITY.get(),pos,state);}
    void geometry(Vector3d anchor,Quaterniond frame){this.anchor=new Vector3d(anchor);this.frame=new Quaterniond(frame);hasGeometry=true;setChanged();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("CouplerVersion",2);tag.putUUID("Endpoint",endpointId);tag.putBoolean("HasGeometry",hasGeometry);
        tag.putDouble("Ax",anchor.x);tag.putDouble("Ay",anchor.y);tag.putDouble("Az",anchor.z);
        tag.putDouble("Qx",frame.x);tag.putDouble("Qy",frame.y);tag.putDouble("Qz",frame.z);tag.putDouble("Qw",frame.w);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        endpointId=tag.hasUUID("Endpoint")?tag.getUUID("Endpoint"):UUID.randomUUID();
        anchor.set(tag.getDouble("Ax"),tag.getDouble("Ay"),tag.getDouble("Az"));frame.set(tag.getDouble("Qx"),tag.getDouble("Qy"),tag.getDouble("Qz"),tag.getDouble("Qw"));
        hasGeometry=tag.getInt("CouplerVersion")==2&&tag.getBoolean("HasGeometry")&&anchor.isFinite()&&frame.isFinite()&&frame.lengthSquared()>.5;
        if(hasGeometry)frame.normalize();else{anchor.set(.5,.5,.5);frame.identity();}
    }
    @Override public void setRemoved(){if(level instanceof net.minecraft.server.level.ServerLevel server)CouplerConnections.unloaded(server,this);super.setRemoved();}
}
