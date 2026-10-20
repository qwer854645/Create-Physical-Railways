package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import java.util.*;
public final class MountBlock extends ComponentBlock implements IRotate,IBE<MountEntity>,BlockSubLevelAssemblyListener {
    private record Transfer(UUID bogie,boolean brake,int heightVersion,RailGauge gauge,BogeyVariant variant,org.joml.Quaterniond worldReference,long expires){}
    private static final Map<GlobalPos,Transfer> TRANSFERS=new HashMap<>();
    MountBlock(Properties p){super(p);}
    private static final VoxelShape SHAPE=Shapes.or(box(0,12,0,16,16,16),box(5,BogieGeometry.SUPPORT_BOTTOM*16,5,11,12,11));
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override public Direction.Axis getRotationAxis(BlockState s){return Direction.Axis.Y;}
    @Override public boolean hasShaftTowards(LevelReader l,BlockPos p,BlockState s,Direction face){return face==Direction.UP;}
    @Override public Class<MountEntity> getBlockEntityClass(){return MountEntity.class;}
    @Override public BlockEntityType<? extends MountEntity> getBlockEntityType(){return Content.MOUNT_ENTITY.get();}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){IBE.onRemove(s,l,p,next);super.onRemove(s,l,p,next,moving);}
    @Override public void beforeMove(ServerLevel from,ServerLevel to,BlockState s,BlockPos a,BlockPos b){
        if(from.getBlockEntity(a) instanceof MountEntity mount&&mount.bogieId!=null){
            org.joml.Quaterniond reference=null;
            if(mount.pivotReferenceKnown&&dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(from,a) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel carrier){
                var pose=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(from).getPipeline().readPose(carrier,new dev.ryanhcode.sable.companion.math.Pose3d());
                reference=new org.joml.Quaterniond(pose.orientation()).mul(mount.pivotReference);
            }
            TRANSFERS.put(GlobalPos.of(to.dimension(),b),new Transfer(mount.bogieId,mount.braking,mount.heightVersion,mount.gauge,mount.variant,reference,to.getGameTime()+20));
        }
    }
    @Override public void afterMove(ServerLevel from,ServerLevel to,BlockState s,BlockPos a,BlockPos b){
        if(to.getBlockEntity(b) instanceof MountEntity mount){restore(mount);VehiclePhysics.register(mount);}
    }
    static void restore(MountEntity mount){
        if(!(mount.getLevel() instanceof ServerLevel level))return;
        TRANSFERS.entrySet().removeIf(e->e.getKey().dimension().equals(level.dimension())&&e.getValue().expires<level.getGameTime());
        var key=GlobalPos.of(level.dimension(),mount.getBlockPos());var t=TRANSFERS.get(key);
        if(t!=null){
            if(t.worldReference!=null){
                if(!(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,mount.getBlockPos()) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel carrier))return;
                var pose=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline().readPose(carrier,new dev.ryanhcode.sable.companion.math.Pose3d());
                mount.pivotReference.set(BogieYawModel.transfer(new org.joml.Quaterniond(),t.worldReference,pose.orientation()));mount.pivotReferenceKnown=true;
            }
            mount.bogieId=t.bogie;mount.braking=t.brake;mount.heightVersion=t.heightVersion;mount.gauge=t.gauge;mount.variant=t.variant;mount.setChanged();TRANSFERS.remove(key);
        }
    }
    static void clear(ServerLevel level){TRANSFERS.keySet().removeIf(p->p.dimension().equals(level.dimension()));}
}
