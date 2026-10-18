package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.UUID;
public final class CouplerEntity extends BlockEntity {
    net.minecraft.world.phys.shapes.VoxelShape livingShape;long livingShapeTick=Long.MIN_VALUE;
    UUID endpointId=UUID.randomUUID();
    Vector3d anchor=new Vector3d(.5,.5,.5);
    Quaterniond frame=new Quaterniond();
    boolean hasGeometry;double restLength=-1;
    boolean visualConnected;UUID visualPeerId;BlockPos visualPeerPos;
    float lockProgress,previousLockProgress;
    void visualPeer(CouplerEntity peer){
        UUID id=peer==null?null:peer.endpointId;BlockPos pos=peer==null?null:peer.getBlockPos().immutable();
        boolean connected=peer!=null;
        if(visualConnected==connected&&java.util.Objects.equals(visualPeerId,id)&&java.util.Objects.equals(visualPeerPos,pos))return;
        livingShape=null;visualConnected=connected;visualPeerId=id;visualPeerPos=pos;syncVisual();
    }
    private void syncVisual(){if(level!=null&&!level.isClientSide&&!isRemoved())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    void clientTick(){previousLockProgress=lockProgress;lockProgress=Math.clamp(lockProgress+(visualConnected?.25f:-.25f),0,1);}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=saveWithoutMetadata(registries);tag.putBoolean("VisualConnected",visualConnected);
        if(visualPeerId!=null&&visualPeerPos!=null){tag.putUUID("VisualPeer",visualPeerId);tag.putLong("VisualPeerPos",visualPeerPos.asLong());}return tag;
    }
    CouplerEntity(BlockPos pos,BlockState state){super(Content.COUPLER_ENTITY.get(),pos,state);}
    void geometry(Vector3d anchor,Quaterniond frame,double length){restLength=length;this.anchor=new Vector3d(anchor);this.frame=new Quaterniond(frame);hasGeometry=true;setChanged();syncVisual();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("CouplerVersion",4);tag.putUUID("Endpoint",endpointId);tag.putBoolean("HasGeometry",hasGeometry);
        tag.putDouble("RestLength",restLength);tag.putDouble("Ax",anchor.x);tag.putDouble("Ay",anchor.y);tag.putDouble("Az",anchor.z);
        tag.putDouble("Qx",frame.x);tag.putDouble("Qy",frame.y);tag.putDouble("Qz",frame.z);tag.putDouble("Qw",frame.w);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);livingShape=null;
        endpointId=tag.hasUUID("Endpoint")?tag.getUUID("Endpoint"):UUID.randomUUID();
        anchor.set(tag.getDouble("Ax"),tag.getDouble("Ay"),tag.getDouble("Az"));frame.set(tag.getDouble("Qx"),tag.getDouble("Qy"),tag.getDouble("Qz"),tag.getDouble("Qw"));
        restLength=tag.getInt("CouplerVersion")>=4&&tag.contains("RestLength")?tag.getDouble("RestLength"):-1;
        if(!Double.isFinite(restLength)||restLength<0)restLength=-1;
        hasGeometry=(tag.getInt("CouplerVersion")>=2&&tag.getInt("CouplerVersion")<=4)&&tag.getBoolean("HasGeometry")&&anchor.isFinite()&&frame.isFinite()&&frame.lengthSquared()>.5;
        if(hasGeometry)frame.normalize();else{anchor.set(.5,.5,.5);frame.identity();}
        visualConnected=tag.getBoolean("VisualConnected");visualPeerId=tag.hasUUID("VisualPeer")?tag.getUUID("VisualPeer"):null;
        visualPeerPos=tag.contains("VisualPeerPos")?BlockPos.of(tag.getLong("VisualPeerPos")):null;
    }
    @Override public void setRemoved(){if(level instanceof net.minecraft.server.level.ServerLevel server)CouplerConnections.unloaded(server,this);super.setRemoved();}
}
