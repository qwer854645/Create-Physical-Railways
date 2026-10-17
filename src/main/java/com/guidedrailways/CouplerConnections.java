package com.guidedrailways;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;
/** One native limited prismatic/ball joint per pair, no scripted impulse or extra rigid body. */
public final class CouplerConnections {
    private record Pending(UUID endpoint,long expires){}
    private static final class Live {
        final GenericConstraintHandle joint;final ServerSubLevel a,b;final BlockPos pa,pb;
        double ma,mb;long nextLog;
        Live(GenericConstraintHandle joint,ServerSubLevel a,ServerSubLevel b,BlockPos pa,BlockPos pb){this.joint=joint;this.a=a;this.b=b;this.pa=pa;this.pb=pb;}
        void close(){if(joint.isValid())joint.remove();}
    }
    private static final class State {
        final Map<UUID,CouplerEntity> endpoints=new HashMap<>();final Map<UUID,Live> live=new HashMap<>();
        final Map<UUID,Pending> pending=new HashMap<>();final Set<UUID> warned=new HashSet<>();
    }
    private record Transfer(UUID endpoint,Vector3d anchor,Quaterniond frame,boolean geometry,GlobalPos source,long expires){}
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    private static final Map<GlobalPos,Transfer> TRANSFERS=new HashMap<>();
    private record Moving(UUID endpoint,long expires){}
    private static final Map<GlobalPos,Moving> MOVING=new HashMap<>();
    private CouplerConnections(){}
    private static State state(ServerLevel level){return STATES.computeIfAbsent(level,l->new State());}
    private static boolean loaded(ServerLevel level,CouplerEntity e){return e!=null&&!e.isRemoved()&&level.hasChunkAt(e.getBlockPos())&&level.getBlockEntity(e.getBlockPos())==e;}
    private static ServerSubLevel body(ServerLevel level,CouplerEntity e){return SableCompanion.INSTANCE.getContaining(level,e.getBlockPos()) instanceof ServerSubLevel b&&!b.isRemoved()?b:null;}
    private static Vector3d blockOrigin(CouplerEntity e){var p=e.getBlockPos();return new Vector3d(p.getX(),p.getY(),p.getZ());}
    private static Vector3d head(CouplerEntity e){var f=e.getBlockState().getValue(CouplerBlock.FACING);return blockOrigin(e).add(.5+f.getStepX()*.48,.5,.5+f.getStepZ()*.48);}
    private static Vector3d facing(ServerSubLevel b,CouplerEntity e){var f=e.getBlockState().getValue(CouplerBlock.FACING);return new Vector3d(f.getStepX(),0,f.getStepZ()).rotate(SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d()).orientation());}
    private static Vector3d up(ServerSubLevel b){return new Vector3d(0,1,0).rotate(SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d()).orientation());}
    private static Vector3d local(ServerSubLevel b,Vector3d world){var pose=SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d());return new Vector3d(world).sub(pose.position()).rotate(new Quaterniond(pose.orientation()).conjugate()).add(b.logicalPose().rotationPoint());}
    private static Quaterniond localFrame(ServerSubLevel b,Quaterniond world){var pose=SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d());return new Quaterniond(pose.orientation()).conjugate().mul(world);}
    static void register(ServerLevel level,CouplerEntity e){
        if(!restore(level,e))return;var s=state(level);var previous=s.endpoints.get(e.endpointId);
        if(previous!=null&&previous!=e&&loaded(level,previous)){
            e.endpointId=UUID.randomUUID();e.hasGeometry=false;e.setChanged();
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("Cloned coupler assigned a new endpoint ID at {}",e.getBlockPos());
        }
        s.endpoints.put(e.endpointId,e);
    }
    static void unloaded(ServerLevel level,CouplerEntity e){var s=STATES.get(level);if(s!=null&&s.endpoints.get(e.endpointId)==e)suspend(level,e.endpointId);}
    static void suspend(ServerLevel level,UUID endpoint){
        var s=STATES.get(level);if(s==null)return;s.endpoints.remove(endpoint);
        var link=CouplerSavedData.get(level).links.forEndpoint(endpoint);if(link!=null)close(s,link.id());
    }
    private static void close(State s,UUID id){var old=s.live.remove(id);if(old!=null)old.close();}
    private static void disconnect(ServerLevel level,UUID endpoint,String reason){
        var data=CouplerSavedData.get(level);var link=data.links.forEndpoint(endpoint);if(link==null)return;
        var s=state(level);close(s,link.id());data.remove(link.id());s.warned.remove(link.id());
        for(var id:List.of(link.a(),link.b())){var e=s.endpoints.get(id);if(e!=null){e.hasGeometry=false;e.setChanged();}}
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[Coupler] disconnected link={} reason={}",link.id(),reason);
    }
    private static boolean moving(ServerLevel level,CouplerEntity e){var moving=MOVING.get(GlobalPos.of(level.dimension(),e.getBlockPos()));return moving!=null&&moving.expires>=level.getGameTime()&&moving.endpoint.equals(e.endpointId);}
    static void destroyed(ServerLevel level,CouplerEntity e){
        if(moving(level,e))return;
        disconnect(level,e.endpointId,"removed");suspend(level,e.endpointId);state(level).pending.values().removeIf(p->p.endpoint.equals(e.endpointId));
    }
    static void tool(ServerLevel level,CouplerEntity e,ServerPlayer player,boolean unlink){
        register(level,e);var s=state(level);var data=CouplerSavedData.get(level);
        if(unlink){s.pending.remove(player.getUUID());if(data.links.forEndpoint(e.endpointId)==null)player.displayClientMessage(Component.literal("这个车钩尚未连接"),true);else{disconnect(level,e.endpointId,"tool");player.displayClientMessage(Component.literal("车钩已解开"),true);}return;}
        if(data.links.forEndpoint(e.endpointId)!=null){player.displayClientMessage(Component.literal("车钩已连接，潜行使用连挂工具可解开"),true);return;}
        var pending=s.pending.get(player.getUUID());
        if(pending==null||pending.expires<level.getGameTime()){
            if(body(level,e)==null)throw new IllegalArgumentException("请先把车钩装到物理车体上");
            s.pending.put(player.getUUID(),new Pending(e.endpointId,level.getGameTime()+600));player.displayClientMessage(Component.literal("已选择车钩，请右键另一节车体上相向的车钩"),true);return;
        }
        if(pending.endpoint.equals(e.endpointId)){s.pending.remove(player.getUUID());player.displayClientMessage(Component.literal("已取消选择车钩"),true);return;}
        var first=s.endpoints.get(pending.endpoint);
        if(!loaded(level,first)){s.pending.remove(player.getUUID());throw new IllegalArgumentException("所选车钩未加载，请重新选择");}
        if(!level.mayInteract(player,first.getBlockPos()))throw new IllegalArgumentException("你没有操作另一端车钩的权限");
        connect(level,first,e);s.pending.remove(player.getUUID());player.displayClientMessage(Component.literal("车钩已连接"),true);
    }
    private static void connect(ServerLevel level,CouplerEntity a,CouplerEntity b){
        var data=CouplerSavedData.get(level);if(data.links.forEndpoint(a.endpointId)!=null||data.links.forEndpoint(b.endpointId)!=null)throw new IllegalArgumentException("其中一端车钩已经连接");
        var ba=body(level,a);var bb=body(level,b);if(ba==null||bb==null)throw new IllegalArgumentException("两端车钩都必须装在物理车体上");
        if(ba==bb)throw new IllegalArgumentException("不能连接同一物理车体上的两个车钩");
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        if(pipe.getLinearVelocity(ba,new Vector3d()).length()>.5||pipe.getLinearVelocity(bb,new Vector3d()).length()>.5||pipe.getAngularVelocity(ba,new Vector3d()).length()>.5||pipe.getAngularVelocity(bb,new Vector3d()).length()>.5)throw new IllegalArgumentException("请先让两节车停稳再连挂");
        var geometry=CouplerGeometry.engage(VehiclePhysics.world(ba,head(a)),facing(ba,a),up(ba),VehiclePhysics.world(bb,head(b)),facing(bb,b),up(bb));
        a.geometry(local(ba,geometry.point()).sub(blockOrigin(a)),localFrame(ba,geometry.frame()));
        b.geometry(local(bb,geometry.point()).sub(blockOrigin(b)),localFrame(bb,geometry.frame()));
        var link=new CouplingLinks.Link(UUID.randomUUID(),a.endpointId,b.endpointId);data.add(link);
        try{reconcile(level,link);if(!state(level).live.containsKey(link.id()))throw new IllegalStateException("车钩关节未能建立，请重新选择");}
        catch(RuntimeException error){disconnect(level,a.endpointId,"creation_failed");throw error;}
    }
    static boolean connected(ServerLevel level,CouplerEntity e){return CouplerSavedData.get(level).links.forEndpoint(e.endpointId)!=null;}
    static boolean blocksRelocation(ServerSubLevel carrier){
        var level=carrier.getLevel();var s=STATES.get(level);if(s==null)return false;
        for(var e:s.endpoints.values())if(loaded(level,e)&&body(level,e)==carrier&&connected(level,e))return true;return false;
    }
    static void step(SubLevelPhysicsSystem physics){
        var level=physics.getLevel();var s=STATES.get(level);if(s==null)return;
        s.endpoints.entrySet().removeIf(e->!loaded(level,e.getValue()));
        var data=CouplerSavedData.get(level);
        for(var id:List.copyOf(s.live.keySet()))if(data.links.get(id)==null)close(s,id);
        for(var link:data.links.all())try{reconcile(level,link);}catch(RuntimeException error){close(s,link.id());if(s.warned.add(link.id()))org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[Coupler] joint rebuild failed link={}",link.id(),error);}
        s.pending.values().removeIf(p->p.expires<level.getGameTime());
        TRANSFERS.entrySet().removeIf(e->e.getKey().dimension().equals(level.dimension())&&e.getValue().expires<level.getGameTime());
        MOVING.entrySet().removeIf(e->e.getKey().dimension().equals(level.dimension())&&e.getValue().expires<level.getGameTime());
    }
    private static void reconcile(ServerLevel level,CouplingLinks.Link link){
        var s=state(level);var a=s.endpoints.get(link.a());var b=s.endpoints.get(link.b());
        if(!loaded(level,a)||!loaded(level,b)){close(s,link.id());return;}
        var ba=body(level,a);var bb=body(level,b);if(ba==null||bb==null){close(s,link.id());return;}
        if(ba==bb){disconnect(level,link.a(),"merged_into_same_body");return;}
        var old=s.live.get(link.id());
        if(old!=null&&(!old.joint.isValid()||old.a!=ba||old.b!=bb||!old.pa.equals(a.getBlockPos())||!old.pb.equals(b.getBlockPos()))){close(s,link.id());old=null;}
        if(!a.hasGeometry||!b.hasGeometry){disconnect(level,link.a(),"missing_saved_geometry");return;}
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        if(old==null){
            var anchorA=blockOrigin(a).add(a.anchor);var anchorB=blockOrigin(b).add(b.anchor);
            double error=VehiclePhysics.world(ba,anchorA).distance(VehiclePhysics.world(bb,anchorB));
            if(error>.35){if(s.warned.add(link.id()))org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[Coupler] rebind deferred link={} savedAnchorError={} (uncouple to reconnect)",link.id(),error);return;}
            GenericConstraintHandle joint=null;
            try{
                joint=pipe.addConstraint(ba,bb,new GenericConstraintConfiguration(anchorA,anchorB,a.frame,b.frame,EnumSet.of(ConstraintJointAxis.LINEAR_Y,ConstraintJointAxis.LINEAR_Z)));
                joint.setLimit(ConstraintJointAxis.LINEAR_X,-CouplerGeometry.HALF_STROKE,CouplerGeometry.HALF_STROKE);
                joint.setContactsEnabled(true);
                old=new Live(joint,ba,bb,a.getBlockPos().immutable(),b.getBlockPos().immutable());tune(old);s.live.put(link.id(),old);s.warned.remove(link.id());
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[CouplerJoint] created link={} bodies={}/{} stroke={} savedAnchorError={}",link.id(),ba.getUniqueId(),bb.getUniqueId(),CouplerGeometry.HALF_STROKE*2,error);
            }catch(RuntimeException errorCreating){if(joint!=null&&joint.isValid())joint.remove();throw errorCreating;}
        }else tune(old);
        if(level.getGameTime()>=old.nextLog){
            old.nextLog=level.getGameTime()+20;
            var wa=VehiclePhysics.world(ba,blockOrigin(a).add(a.anchor));var wb=VehiclePhysics.world(bb,blockOrigin(b).add(b.anchor));
            if(level.players().stream().anyMatch(p->p.distanceToSqr(wa.x,wa.y,wa.z)<4096)){
                var frameWorld=new Quaterniond(pipe.readPose(ba,new Pose3d()).orientation()).mul(a.frame);
                var delta=new Vector3d(wb).sub(wa).rotate(frameWorld.conjugate());
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[CouplerState] link={} bufferX={} transverseError={} supportedMassA={} supportedMassB={} joint={}",link.id(),delta.x,Math.hypot(delta.y,delta.z),old.ma,old.mb,old.joint.isValid());
            }
        }
    }
    private static void tune(Live live){
        double ma=VehiclePhysics.coupledMass(live.a),mb=VehiclePhysics.coupledMass(live.b);
        if(Math.abs(live.ma-ma)<.01&&Math.abs(live.mb-mb)<.01)return;
        var spring=CouplerGeometry.spring(ma,mb);live.joint.setMotor(ConstraintJointAxis.LINEAR_X,0,spring.stiffness(),spring.damping(),true,spring.maxForce());live.ma=ma;live.mb=mb;
    }
    static void beforeMove(ServerLevel from,ServerLevel to,BlockPos oldPos,BlockPos newPos){
        if(!(from.getBlockEntity(oldPos) instanceof CouplerEntity e))return;
        if(from!=to)disconnect(from,e.endpointId,"dimension_changed");
        var b=body(from,e);Vector3d worldAnchor=null;Quaterniond worldFrame=null;
        if(b!=null&&e.hasGeometry){worldAnchor=VehiclePhysics.world(b,blockOrigin(e).add(e.anchor));worldFrame=new Quaterniond(SubLevelPhysicsSystem.get(from).getPipeline().readPose(b,new Pose3d()).orientation()).mul(e.frame);}
        var source=GlobalPos.of(from.dimension(),oldPos.immutable());MOVING.put(source,new Moving(e.endpointId,from.getGameTime()+200));
        TRANSFERS.put(GlobalPos.of(to.dimension(),newPos.immutable()),new Transfer(e.endpointId,worldAnchor,worldFrame,e.hasGeometry&&worldAnchor!=null,source,to.getGameTime()+200));suspend(from,e.endpointId);
    }
    static void afterMove(ServerLevel to,BlockPos pos){if(to.getBlockEntity(pos) instanceof CouplerEntity e)register(to,e);}
    private static boolean restore(ServerLevel level,CouplerEntity e){
        var key=GlobalPos.of(level.dimension(),e.getBlockPos());var transfer=TRANSFERS.get(key);if(transfer==null)return true;
        e.endpointId=transfer.endpoint;
        if(transfer.geometry){var b=body(level,e);if(b==null||b.getMassTracker().getMass()<=0)return false;e.geometry(local(b,transfer.anchor).sub(blockOrigin(e)),localFrame(b,transfer.frame));}
        else{e.hasGeometry=false;e.setChanged();}
        TRANSFERS.remove(key);MOVING.put(transfer.source,new Moving(e.endpointId,level.getGameTime()+5));return true;
    }
    static void unload(ServerLevel level){
        var s=STATES.remove(level);if(s!=null)s.live.values().forEach(Live::close);
        TRANSFERS.entrySet().removeIf(e->e.getKey().dimension().equals(level.dimension())||e.getValue().source.dimension().equals(level.dimension()));
        MOVING.keySet().removeIf(p->p.dimension().equals(level.dimension()));
    }
}
