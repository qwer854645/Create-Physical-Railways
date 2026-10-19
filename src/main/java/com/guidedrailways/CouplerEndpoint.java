package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.UUID;
/** Saved coupling data attached to the actual owning block entity; never another placed block entity. */
public final class CouplerEndpoint {
    public interface Provider { CouplerEndpoint guidedrailways$endpoint(); }
    final BlockEntity owner;
    public CouplerEndpoint(BlockEntity owner){this.owner=owner;}
    public static CouplerEndpoint get(BlockEntity owner){return owner instanceof CouplerEntity own?own.endpoint:owner instanceof Provider provider?provider.guidedrailways$endpoint():null;}
    public BlockPos getBlockPos(){return owner.getBlockPos();}
    public BlockState getBlockState(){return owner.getBlockState();}
    public Level getLevel(){return owner.getLevel();}
    public boolean isRemoved(){return owner.isRemoved();}
    public void setChanged(){owner.setChanged();}
    public void tick(){if(getLevel() instanceof net.minecraft.server.level.ServerLevel level)CouplerConnections.register(level,this);else clientTick();}
    public void unloaded(){if(getLevel() instanceof net.minecraft.server.level.ServerLevel level)CouplerConnections.unloaded(level,this);}
    public void destroyed(){if(getLevel() instanceof net.minecraft.server.level.ServerLevel level)CouplerConnections.destroyed(level,this);}
    public void link(net.minecraft.server.level.ServerPlayer player){CouplerConnections.link(player.serverLevel(),this,player);}
    public static void beforeMove(net.minecraft.server.level.ServerLevel from,net.minecraft.server.level.ServerLevel to,BlockPos oldPos,BlockPos newPos){CouplerConnections.beforeMove(from,to,oldPos,newPos);}
    public static void afterMove(net.minecraft.server.level.ServerLevel to,BlockPos pos){CouplerConnections.afterMove(to,pos);}
    net.minecraft.world.phys.shapes.VoxelShape livingShape;long livingShapeTick=Long.MIN_VALUE;
    String hardwareFrame;
    UUID endpointId=UUID.randomUUID();
    Vector3d anchor=new Vector3d(.5,.5,.5);
    Quaterniond frame=new Quaterniond();
    boolean hasGeometry;double restLength=-1;
    boolean visualConnected;UUID visualPeerId;BlockPos visualPeerPos;
    float lockProgress,previousLockProgress;
    void visualPeer(CouplerEndpoint peer){
        UUID id=peer==null?null:peer.endpointId;BlockPos pos=peer==null?null:peer.getBlockPos().immutable();
        boolean connected=peer!=null;
        if(visualConnected==connected&&java.util.Objects.equals(visualPeerId,id)&&java.util.Objects.equals(visualPeerPos,pos))return;
        livingShape=null;visualConnected=connected;visualPeerId=id;visualPeerPos=pos;syncVisual();
    }
    private void syncVisual(){if(getLevel()!=null&&!getLevel().isClientSide&&!isRemoved())getLevel().sendBlockUpdated(getBlockPos(),getBlockState(),getBlockState(),2);}
    void clientTick(){previousLockProgress=lockProgress;lockProgress=Math.clamp(lockProgress+(visualConnected?.25f:-.25f),0,1);}
    void geometry(Vector3d anchor,Quaterniond frame,double length){livingShape=null;restLength=length;this.anchor=new Vector3d(anchor);this.frame=new Quaterniond(frame);hasGeometry=true;setChanged();syncVisual();}
    public void write(CompoundTag tag){
        tag.putInt("CouplerVersion",4);tag.putUUID("Endpoint",endpointId);tag.putBoolean("HasGeometry",hasGeometry);
        tag.putDouble("RestLength",restLength);tag.putDouble("Ax",anchor.x);tag.putDouble("Ay",anchor.y);tag.putDouble("Az",anchor.z);
        tag.putDouble("Qx",frame.x);tag.putDouble("Qy",frame.y);tag.putDouble("Qz",frame.z);tag.putDouble("Qw",frame.w);
    }
    public void read(CompoundTag tag){
        livingShape=null;
        endpointId=tag.hasUUID("Endpoint")?tag.getUUID("Endpoint"):UUID.randomUUID();
        anchor.set(tag.getDouble("Ax"),tag.getDouble("Ay"),tag.getDouble("Az"));frame.set(tag.getDouble("Qx"),tag.getDouble("Qy"),tag.getDouble("Qz"),tag.getDouble("Qw"));
        restLength=tag.getInt("CouplerVersion")>=4&&tag.contains("RestLength")?tag.getDouble("RestLength"):-1;
        if(!Double.isFinite(restLength)||restLength<0)restLength=-1;
        hasGeometry=(tag.getInt("CouplerVersion")>=2&&tag.getInt("CouplerVersion")<=4)&&tag.getBoolean("HasGeometry")&&anchor.isFinite()&&frame.isFinite()&&frame.lengthSquared()>.5;
        if(hasGeometry)frame.normalize();else{anchor.set(.5,.5,.5);frame.identity();}
        visualConnected=tag.getBoolean("VisualConnected");visualPeerId=tag.hasUUID("VisualPeer")?tag.getUUID("VisualPeer"):null;
        visualPeerPos=tag.contains("VisualPeerPos")?BlockPos.of(tag.getLong("VisualPeerPos")):null;
    }

    public CompoundTag save(){var tag=new CompoundTag();write(tag);tag.putBoolean("VisualConnected",visualConnected);if(visualPeerId!=null&&visualPeerPos!=null){tag.putUUID("VisualPeer",visualPeerId);tag.putLong("VisualPeerPos",visualPeerPos.asLong());}return tag;}
}
