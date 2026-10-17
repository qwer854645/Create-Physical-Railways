package com.guidedrailways;

import com.simibubi.create.content.trains.track.ITrackBlock;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;
import org.joml.Matrix3d;
import java.util.*;

/** Independently written guided-body prototype. No per-wheel bodies or patched native library. */
public final class VehiclePhysics {
    static final double RIDE_HEIGHT=BogieGeometry.RIDE_HEIGHT,HALF_WHEELBASE=1.0,PLATE_OFFSET=.5;
    private static final Quaterniond ANCHOR_ROT=new Quaterniond().rotateY(-Math.PI/2);
    private static final Map<ServerLevel,Map<BlockPos,MountEntity>> INPUTS=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Unit>> ACTIVE=new WeakHashMap<>();
    static final class Unit {
        final MountEntity mount;final ServerSubLevel carrier,bogie;final Vector3d localCenter;
        GenericConstraintHandle pivot;final GenericConstraintHandle[] guides=new GenericConstraintHandle[2];
        final TrackNetwork.Path[] routes=new TrackNetwork.Path[2];
        final TrackNetwork.Sample[] samples=new TrackNetwork.Sample[2];
        double mass,speed;long lastSeen,nextLog;boolean detached;int supports=1;
        Unit(MountEntity mount,ServerSubLevel carrier,ServerSubLevel bogie){this.mount=mount;this.carrier=carrier;this.bogie=bogie;localCenter=center(bogie);}
        Vector3d axle(int i){return new Vector3d(localCenter).add(0,-RIDE_HEIGHT,i==0?HALF_WHEELBASE:-HALF_WHEELBASE);}
        void clearGuides(){for(int i=0;i<2;i++){if(guides[i]!=null&&guides[i].isValid())guides[i].remove();guides[i]=null;}}
        void close(){clearGuides();if(pivot!=null&&pivot.isValid())pivot.remove();}
    }
    private VehiclePhysics(){}
    static Vector3d center(ServerSubLevel body){var p=body.getPlot().getCenterBlock();return new Vector3d(p.getX()+.5,p.getY()+.5,p.getZ()+.5);}
    static Vector3d world(ServerSubLevel body,Vector3dc local){var pose=SubLevelPhysicsSystem.get(body.getLevel()).getPipeline().readPose(body,new Pose3d());return new Vector3d(local).sub(body.logicalPose().rotationPoint()).rotate(pose.orientation()).add(pose.position());}
    static boolean hasVehicles(ServerLevel level){return !INPUTS.getOrDefault(level,Map.of()).isEmpty()||!ACTIVE.getOrDefault(level,Map.of()).isEmpty();}
    static void register(MountEntity mount){if(mount.getLevel() instanceof ServerLevel level&&mount.bogieId!=null)INPUTS.computeIfAbsent(level,l->new HashMap<>()).put(mount.getBlockPos().immutable(),mount);}
    static void tick(ServerLevel level){
        var container=SubLevelContainer.getContainer(level);var system=SubLevelPhysicsSystem.get(level);if(container==null||system==null)return;
        var inputs=INPUTS.computeIfAbsent(level,l->new HashMap<>());inputs.values().removeIf(m->m.isRemoved()||m.bogieId==null||level.getBlockEntity(m.getBlockPos())!=m);
        var units=ACTIVE.computeIfAbsent(level,l->new HashMap<>());
        for(var mount:inputs.values()){
            if(!(SableCompanion.INSTANCE.getContaining(level,mount.getBlockPos()) instanceof ServerSubLevel carrier)||carrier.isRemoved())continue;
            if(!(container.getSubLevel(mount.bogieId) instanceof ServerSubLevel bogie)||bogie.isRemoved()||bogie==carrier)continue;
            if(!populateCollision(bogie,true))continue;
            var unit=units.get(mount.bogieId);
            if(unit==null||unit.carrier!=carrier||unit.mount!=mount){
                if(unit!=null)unit.close();unit=new Unit(mount,carrier,bogie);connectPivot(unit);units.put(mount.bogieId,unit);
            }
            unit.lastSeen=level.getGameTime();
        }
        units.entrySet().removeIf(e->{var u=e.getValue();if(u.carrier.isRemoved()||u.bogie.isRemoved()||level.getGameTime()-u.lastSeen>40){u.close();return true;}return false;});
        PerfCounters.report(level,units.size());
        var counts=new HashMap<UUID,Integer>();for(var u:units.values())counts.merge(u.carrier.getUniqueId(),1,Integer::sum);
        for(var u:units.values()){
            int supports=counts.get(u.carrier.getUniqueId());
            if(u.supports!=supports){if(u.pivot!=null&&u.pivot.isValid())u.pivot.remove();u.supports=supports;connectPivot(u);}
            u.mass=u.carrier.getMassTracker().getMass()/supports+u.bogie.getMassTracker().getMass();u.mount.setLoad(u.mass);
            if(level.getBlockEntity(u.bogie.getPlot().getCenterBlock()) instanceof BogieEntity visual){visual.speed=u.speed;visual.distance+=u.speed/20;if(level.getGameTime()%2==0)visual.sendData();}
            if(level.getGameTime()>=u.nextLog){u.nextLog=level.getGameTime()+20;
                var pos=world(u.bogie,u.localCenter);if(level.players().stream().anyMatch(p->p.distanceToSqr(pos.x,pos.y,pos.z)<4096)){
                    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuidedBogie] body={} speed={} target={} inputRpm={} supportedMass={} brakes={} guided={} carrier={} mounts={} pivot={} anchors={}",u.bogie.getUniqueId(),u.speed,u.mount.getSpeed()*TrainConfig.SPEED_PER_RPM.get(),u.mount.getSpeed(),u.mass,u.mount.braking,!u.detached,u.carrier.getUniqueId(),counts.get(u.carrier.getUniqueId()),TrainConfig.DETAILED_LOGGING.get()?pivotSummary(u):"off",TrainConfig.DETAILED_LOGGING.get()?anchorSummary(u):"off");
                }
            }
        }
    }
    private static String pivotSummary(Unit u){
        var pipe=SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
        var upper=new Vector3d(u.mount.getBlockPos().getX()+.5,u.mount.getBlockPos().getY()+.375,u.mount.getBlockPos().getZ()+.5);
        var lower=new Vector3d(u.localCenter).add(0,.375,0);
        var a=pipe.readPose(u.carrier,new Pose3d());var b=pipe.readPose(u.bogie,new Pose3d());
        var relative=new Quaterniond(b.orientation()).conjugate().mul(a.orientation());
        var rate=pipe.getAngularVelocity(u.carrier,new Vector3d()).sub(pipe.getAngularVelocity(u.bogie,new Vector3d())).rotate(new Quaterniond(b.orientation()).conjugate());
        var up=new Vector3d(0,1,0).rotate(relative);
        double tilt=Math.toDegrees(Math.acos(Math.clamp(up.y,-1,1)));
        return String.format(java.util.Locale.ROOT,"anchorError=%.5f,tiltDeg=%.3f,yawRateDeg=%.3f,pitchRateDeg=%.3f,rollRateDeg=%.3f,mode=%s",world(u.carrier,upper).distance(world(u.bogie,lower)),tilt,Math.toDegrees(rate.y),Math.toDegrees(rate.x),Math.toDegrees(rate.z),u.supports==1?"stable":"articulated");
    }
    private static String anchorSummary(Unit u){
        var text=new StringBuilder();
        var pipe=SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
        var forward=new Vector3d(0,0,1).rotate(pipe.readPose(u.bogie,new Pose3d()).orientation());
        for(int i=0;i<2;i++){
            if(i>0)text.append(';');var s=u.samples[i];
            if(s==null){text.append("missing");continue;}
            text.append(String.format(java.util.Locale.ROOT,"%s:%d-%d,t=%.4f,curve=%s,error=%.5f,align=%.4f,joint=%s",i==0?"front":"rear",s.path().a(),s.path().b(),s.t(),s.path().edge().isTurn(),world(u.bogie,u.axle(i)).distance(s.position()),forward.dot(s.tangent()),u.guides[i]!=null&&u.guides[i].isValid()));
        }
        return text.toString();
    }
    private static void connectPivot(Unit u){
        var pipe=SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
        var upper=new Vector3d(u.mount.getBlockPos().getX()+.5,u.mount.getBlockPos().getY()+.375,u.mount.getBlockPos().getZ()+.5);
        var lower=new Vector3d(u.localCenter).add(0,.375,0);
        var locks=EnumSet.of(ConstraintJointAxis.LINEAR_X,ConstraintJointAxis.LINEAR_Y,ConstraintJointAxis.LINEAR_Z,ConstraintJointAxis.ANGULAR_Z);
        if(u.supports==1)locks.add(ConstraintJointAxis.ANGULAR_X);
        u.pivot=pipe.addConstraint(u.carrier,u.bogie,new GenericConstraintConfiguration(upper,lower,new Quaterniond(),new Quaterniond(),locks));
        u.pivot.setContactsEnabled(false);
        // A stable bearing replaces the prototype's spring-supported empty plate.
        // Zero stiffness means no yaw-centering torque: only relative rotation is damped.
        u.pivot.setMotor(ConstraintJointAxis.ANGULAR_Y,0,0,6,true,2000);
        if(u.supports>1){
            // Bogies at opposite ends of a long car may meet different grades.
            u.pivot.setLimit(ConstraintJointAxis.ANGULAR_X,-.35,.35);
            u.pivot.setMotor(ConstraintJointAxis.ANGULAR_X,0,0,12,true,4000);
        }
    }
    static void step(SubLevelPhysicsSystem physics,double dt){
        if(!TrainConfig.PERFORMANCE_LOGGING.get()){stepVehicle(physics,dt);return;}
        long start=System.nanoTime();try{stepVehicle(physics,dt);}finally{PerfCounters.step(physics.getLevel(),System.nanoTime()-start);}
    }
    private static void stepVehicle(SubLevelPhysicsSystem physics,double dt){
        if(!(dt>0&&Double.isFinite(dt)))return;var level=physics.getLevel();var pipe=physics.getPipeline();
        for(var u:ACTIVE.getOrDefault(level,Map.of()).values()){
            if(u.carrier.isRemoved()||u.bogie.isRemoved()||u.mount.isRemoved()||u.lastSeen+2<level.getGameTime())continue;
            var pose=pipe.readPose(u.bogie,new Pose3d());var heading=new Vector3d(0,0,1).rotate(pose.orientation());
            if(u.detached){u.speed=pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(heading);continue;}
            boolean valid=true;
            for(int i=0;i<2;i++){
                var at=world(u.bogie,u.axle(i));u.samples[i]=TrackNetwork.follow(level,at,heading,u.samples[i],.8);
                if(u.samples[i]==null){valid=false;break;}
            }
            if(!valid){u.clearGuides();u.detached=true;org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuideReleased] body={} reason=track_missing_or_out_of_range anchors={}",u.bogie.getUniqueId(),anchorSummary(u));continue;}
            u.speed=0;
            for(int i=0;i<2;i++){
                var sample=u.samples[i];u.routes[i]=sample.path();var q=sample.orientation();
                if(u.guides[i]==null||!u.guides[i].isValid()){
                    u.guides[i]=pipe.addConstraint(null,u.bogie,new GenericConstraintConfiguration(sample.position(),u.axle(i),q,ANCHOR_ROT,EnumSet.of(ConstraintJointAxis.LINEAR_Y,ConstraintJointAxis.LINEAR_Z,ConstraintJointAxis.ANGULAR_X)));
                    u.guides[i].setContactsEnabled(false);
                }else u.guides[i].setFrame1(sample.position(),q);
                // Longitudinal translation is not locked: the solver never sets the vehicle's forward position.
                double speed=pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(sample.tangent());u.speed+=speed*.5;
                double force=DriveModel.force(speed,u.mount.getSpeed(),u.mass,u.mount.braking,TrainConfig.SPEED_PER_RPM.get(),TrainConfig.FORCE_PER_RPM.get(),TrainConfig.ACCELERATION.get(),TrainConfig.BRAKING.get());
                var impulse=new Vector3d(sample.tangent()).mul(force*dt*.5).rotate(new Quaterniond(pose.orientation()).conjugate());
                // Apply traction at the frame centre height, with front/rear positions distributed along its length.
                var point=new Vector3d(u.localCenter).add(0,0,i==0?HALF_WHEELBASE:-HALF_WHEELBASE);
                pipe.applyImpulse(u.bogie,point,impulse);
            }
        }
    }
    static Unit owner(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server))return null;var units=ACTIVE.getOrDefault(server,Map.of());
        if(level.getBlockEntity(pos) instanceof MountEntity mount&&mount.bogieId!=null)return units.get(mount.bogieId);
        if(SableCompanion.INSTANCE.getContaining(level,pos) instanceof ServerSubLevel b)return units.get(b.getUniqueId());return null;
    }
    static void dismantle(Unit u,Player player){
        var level=u.bogie.getLevel();u.close();ACTIVE.getOrDefault(level,Map.of()).remove(u.bogie.getUniqueId());
        var container=SubLevelContainer.getContainer(level);container.removeSubLevel(u.bogie,SubLevelRemovalReason.REMOVED);
        u.mount.bogieId=null;level.setBlock(u.mount.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
        // Removing the final mount can already remove the carrier's native body.
        if(!u.carrier.isRemoved()){
            u.carrier.buildMassTracker();u.carrier.updateMergedMassData(1);
            if(u.carrier.getMassTracker().getMass()<=.001){if(!u.carrier.isRemoved())container.removeSubLevel(u.carrier,SubLevelRemovalReason.REMOVED);}
            else if(!u.carrier.isRemoved())SubLevelPhysicsSystem.get(level).getPipeline().onStatsChanged(u.carrier);
        }
        if(!player.getAbilities().instabuild){var stack=new ItemStack(Content.BOGIE_ITEM.get());if(!player.getInventory().add(stack))player.drop(stack,false);}
    }
    static void place(ServerLevel level,UseOnContext context){
        if(SubLevelPhysicsSystem.get(level)==null)throw new IllegalStateException("物理系统尚未就绪");
        TrackNetwork.refresh(level);var player=context.getPlayer();var clicked=context.getClickedPos();var state=level.getBlockState(clicked);
        var carrierBody=SableCompanion.INSTANCE.getContaining(level,clicked);
        var look=player.getLookAngle();var heading=new Vector3d(look.x,0,look.z).normalize();
        if(carrierBody instanceof ServerSubLevel carrier){
            if(context.getClickedFace()!=Direction.DOWN)throw new IllegalArgumentException("请右键物理车体底部或机械动力轨道");
            var pos=clicked.below();if(!level.getBlockState(pos).canBeReplaced())throw new IllegalArgumentException("底部没有安装空间");
            var upper=world(carrier,new Vector3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));
            var wanted=new Vector3d(upper).add(new Vector3d(0,-(RIDE_HEIGHT+PLATE_OFFSET),0).rotate(carrier.logicalPose().orientation()));
            var sample=TrackNetwork.nearest(level,wanted,heading,null,1.0);if(sample==null)throw new IllegalArgumentException("安装位置附近没有可用轨道");
            var rotation=bodyOrientation(sample);var origin=new Vector3d(upper).add(new Vector3d(0,-PLATE_OFFSET,0).rotate(rotation));
            validatePlacement(level,origin,rotation);var bogie=createBody(level,Content.BOGIE.get().defaultBlockState(),origin,rotation);
            try{
                level.setBlock(pos,Content.MOUNT.get().defaultBlockState(),3);
                if(!(level.getBlockEntity(pos) instanceof MountEntity mount))throw new IllegalStateException("安装座初始化失败");
                mount.bogieId=bogie.getUniqueId();mount.setChanged();register(mount);carrier.buildMassTracker();carrier.updateMergedMassData(1);SubLevelPhysicsSystem.get(level).getPipeline().onStatsChanged(carrier);tick(level);
            }catch(RuntimeException e){SubLevelContainer.getContainer(level).removeSubLevel(bogie,SubLevelRemovalReason.REMOVED);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);throw e;}
            return;
        }
        if(!(state.getBlock() instanceof ITrackBlock))throw new IllegalArgumentException("请右键机械动力轨道或物理车体底部");
        var click=context.getClickLocation();var sample=TrackNetwork.nearest(level,new Vector3d(click.x,click.y,click.z),heading,null,1.5);
        if(sample==null)throw new IllegalArgumentException("轨道网络尚未就绪，请稍后再试");
        var rotation=bodyOrientation(sample);var origin=new Vector3d(sample.position()).add(new Vector3d(sample.up()).mul(RIDE_HEIGHT));validatePlacement(level,origin,rotation);
        ServerSubLevel bogie=null,carrier=null;
        try{
            bogie=createBody(level,Content.BOGIE.get().defaultBlockState(),origin,rotation);
            carrier=createBody(level,Content.MOUNT.get().defaultBlockState(),new Vector3d(origin).add(new Vector3d(sample.up()).mul(PLATE_OFFSET)),rotation);
            var mount=(MountEntity)level.getBlockEntity(carrier.getPlot().getCenterBlock());if(mount==null)throw new IllegalStateException("安装座初始化失败");
            mount.bogieId=bogie.getUniqueId();mount.setChanged();register(mount);tick(level);
        }catch(RuntimeException e){var c=SubLevelContainer.getContainer(level);if(bogie!=null&&!bogie.isRemoved())c.removeSubLevel(bogie,SubLevelRemovalReason.REMOVED);if(carrier!=null&&!carrier.isRemoved())c.removeSubLevel(carrier,SubLevelRemovalReason.REMOVED);throw e;}
    }
    private static Quaterniond bodyOrientation(TrackNetwork.Sample sample){var z=new Vector3d(sample.tangent());var y=new Vector3d(sample.up());var x=new Vector3d(y).cross(z).normalize();y.set(z).cross(x).normalize();return new Quaterniond().setFromNormalized(new Matrix3d(x,y,z));}
    private static void validatePlacement(ServerLevel level,Vector3d origin,Quaterniond rotation){
        if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(origin.x,origin.y,origin.z)))throw new IllegalArgumentException("安装位置超出世界边界");
        var forward=new Vector3d(0,0,1).rotate(rotation);
        for(int i=0;i<2;i++){
            var axle=new Vector3d(0,-RIDE_HEIGHT,i==0?HALF_WHEELBASE:-HALF_WHEELBASE).rotate(rotation).add(origin);
            if(TrackNetwork.nearest(level,axle,forward,null,.25)==null)throw new IllegalArgumentException("前后轮轴下方需要连续轨道");
        }
        for(var u:ACTIVE.getOrDefault(level,Map.of()).values())if(world(u.bogie,u.localCenter).distanceSquared(origin)<4)throw new IllegalArgumentException("这里已有转向架");
    }
    private static boolean populateCollision(ServerSubLevel body,boolean refreshMass){
        var tag=body.getUserDataTag();if(tag.getInt("GuidedCollisionLayout")>=1)return true;
        var level=body.getLevel();var centre=body.getPlot().getCenterBlock();
        for(var cell:BogieCollision.CELLS){
            if(cell.part()==BogieCollision.CENTRE_PART)continue;
            var state=level.getBlockState(centre.offset(cell.x(),cell.y(),cell.z()));
            if(!state.isAir()&&!state.is(Content.COLLISION.get()))return false;
        }
        for(var cell:BogieCollision.CELLS){
            if(cell.part()==BogieCollision.CENTRE_PART)continue;
            var pos=centre.offset(cell.x(),cell.y(),cell.z());var state=Content.COLLISION.get().defaultBlockState().setValue(CollisionPartBlock.PART,cell.part());
            var chunk=level.getChunkAt(pos);var old=chunk.getBlockState(pos);if(old.equals(state))continue;
            chunk.setBlockState(pos,state,true);SubLevelAssemblyHelper.markAndNotifyBlock(level,pos,chunk,old,state,3,512);
        }
        tag.putInt("GuidedCollisionLayout",1);body.setUserDataTag(tag);
        if(refreshMass){body.buildMassTracker();body.updateMergedMassData(1);SubLevelPhysicsSystem.get(level).getPipeline().onStatsChanged(body);body.updateBoundingBox();}
        return true;
    }
    private static ServerSubLevel createBody(ServerLevel level,BlockState state,Vector3d origin,Quaterniond rotation){
        var container=SubLevelContainer.getContainer(level);var body=(ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        try{
            var plot=body.getPlot();plot.newEmptyChunk(plot.getCenterChunk());var pos=plot.getCenterBlock();var ref=center(body);var chunk=level.getChunkAt(pos);var old=chunk.getBlockState(pos);chunk.setBlockState(pos,state,true);SubLevelAssemblyHelper.markAndNotifyBlock(level,pos,chunk,old,state,3,512);
            if(state.is(Content.BOGIE.get())&&!populateCollision(body,false))throw new IllegalStateException("转向架碰撞占位初始化失败");
            body.buildMassTracker();body.updateMergedMassData(1);var com=body.getMassTracker().getCenterOfMass();body.logicalPose().rotationPoint().set(com);
            SubLevelPhysicsSystem.get(level).getPipeline().teleport(body,new Vector3d(com).sub(ref).rotate(rotation).add(origin),rotation);body.updateLastPose();body.updateBoundingBox();body.setName(state.is(Content.MOUNT.get())?"自由车体安装座":"轨道导向转向架");return body;
        }catch(RuntimeException error){container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);throw error;}
    }
    static void unload(ServerLevel level){var units=ACTIVE.remove(level);if(units!=null)units.values().forEach(Unit::close);INPUTS.remove(level);MountBlock.clear(level);PerfCounters.unload(level);}
}
