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
/** One native radial spring/limit per pair; both mounting sockets swivel freely. */
public final class CouplerConnections {
    private record Pending(UUID endpoint,long expires){}
    private static final class Live {
        final GenericConstraintHandle joint;final ServerSubLevel a,b;final BlockPos pa,pb;final CouplerEndpoint visualA,visualB;
        double ma,mb;long nextLog;boolean captured;
        Live(GenericConstraintHandle joint,ServerSubLevel a,ServerSubLevel b,CouplerEndpoint ea,CouplerEndpoint eb){this.joint=joint;this.a=a;this.b=b;this.pa=ea.getBlockPos().immutable();this.pb=eb.getBlockPos().immutable();visualA=ea;visualB=eb;}
        void close(){close(true);}
        void close(boolean notify){if(joint.isValid())joint.remove();if(notify){visualA.visualPeer(null);visualB.visualPeer(null);}}
    }
    private static final class State {
        final Map<UUID,CouplerEndpoint> endpoints=new HashMap<>();final Map<UUID,Live> live=new HashMap<>();
        final Map<UUID,Pending> pending=new HashMap<>();final Set<UUID> warned=new HashSet<>();
    }
    private record Transfer(UUID endpoint,Vector3d anchor,Quaterniond frame,boolean geometry,double length,GlobalPos source,long expires){}
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    private static final Map<GlobalPos,Transfer> TRANSFERS=new HashMap<>();
    private record Moving(UUID endpoint,long expires){}
    private static final Map<GlobalPos,Moving> MOVING=new HashMap<>();
    private CouplerConnections(){}
    private static State state(ServerLevel level){return STATES.computeIfAbsent(level,l->new State());}
    private static boolean loaded(ServerLevel level,CouplerEndpoint e){return e!=null&&!e.isRemoved()&&level.hasChunkAt(e.getBlockPos())&&level.getBlockEntity(e.getBlockPos())==e.owner;}
    private static ServerSubLevel body(ServerLevel level,CouplerEndpoint e){return SableCompanion.INSTANCE.getContaining(level,e.getBlockPos()) instanceof ServerSubLevel b&&!b.isRemoved()?b:null;}
    private static Vector3d blockOrigin(CouplerEndpoint e){var p=e.getBlockPos();return new Vector3d(p.getX(),p.getY(),p.getZ());}
    private static Vector3d socket(CouplerEndpoint e){return blockOrigin(e).add(RailwayHardware.pivot(e));}
    private static Vector3d head(CouplerEndpoint e){return blockOrigin(e).add(RailwayHardware.tip(e));}
    private static Vector3d facing(ServerSubLevel b,CouplerEndpoint e){var f=e.getBlockState().getValue(CouplerBlock.FACING);return new Vector3d(f.getStepX(),0,f.getStepZ()).rotate(SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d()).orientation());}
    private static Vector3d up(ServerSubLevel b){return new Vector3d(0,1,0).rotate(SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d()).orientation());}
    private static Vector3d local(ServerSubLevel b,Vector3d world){var pose=SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d());return new Vector3d(world).sub(pose.position()).rotate(new Quaterniond(pose.orientation()).conjugate()).add(b.logicalPose().rotationPoint());}
    private static Quaterniond localFrame(ServerSubLevel b,Quaterniond world){var pose=SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d());return new Quaterniond(pose.orientation()).conjugate().mul(world);}
    static void register(ServerLevel level,CouplerEndpoint e){
        if(!restore(level,e))return;
        var frame=String.valueOf(RailwayHardware.kind(e))+":"+e.getBlockState().getValue(CouplerBlock.FACING);
        var flip=e.getBlockState().getProperties().stream().filter(p->p.getName().equals("upside_down")).findFirst();
        if(flip.isPresent())frame+=":"+e.getBlockState().getValue(flip.get());
        if(e.hardwareFrame!=null&&!e.hardwareFrame.equals(frame))disconnect(level,e.endpointId,"hardware_changed");
        e.hardwareFrame=frame;var s=state(level);var previous=s.endpoints.get(e.endpointId);
        if(previous!=null&&previous!=e&&loaded(level,previous)){
            e.endpointId=UUID.randomUUID();e.hasGeometry=false;e.setChanged();
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("Cloned coupler assigned a new endpoint ID at {}",e.getBlockPos());
        }
        s.endpoints.put(e.endpointId,e);
    }
    static void unloaded(ServerLevel level,CouplerEndpoint e){var s=STATES.get(level);if(s!=null&&s.endpoints.get(e.endpointId)==e)suspend(level,e.endpointId);}
    static void suspend(ServerLevel level,UUID endpoint){
        var s=STATES.get(level);if(s==null)return;s.endpoints.remove(endpoint);
        // Unloading a plot must not send block updates into a holder being removed.
        // Keep saved links; endpoint registration will rebuild the joint and visuals.
        var link=CouplerSavedData.get(level).links.forEndpoint(endpoint);if(link!=null){var old=s.live.remove(link.id());if(old!=null)old.close(false);}
    }
    private static void close(State s,UUID id){var old=s.live.remove(id);if(old!=null)old.close();}
    private static void disconnect(ServerLevel level,UUID endpoint,String reason){
        var data=CouplerSavedData.get(level);var link=data.links.forEndpoint(endpoint);if(link==null)return;
        var s=state(level);close(s,link.id());data.remove(link.id());s.warned.remove(link.id());
        for(var id:List.of(link.a(),link.b())){var e=s.endpoints.get(id);if(e!=null){e.hasGeometry=false;e.setChanged();e.visualPeer(null);}}
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[Coupler] disconnected link={} reason={}",link.id(),reason);
    }
    private static boolean moving(ServerLevel level,CouplerEndpoint e){var moving=MOVING.get(GlobalPos.of(level.dimension(),e.getBlockPos()));return moving!=null&&moving.expires>=level.getGameTime()&&moving.endpoint.equals(e.endpointId);}
    static void destroyed(ServerLevel level,CouplerEndpoint e){
        if(moving(level,e))return;
        disconnect(level,e.endpointId,"removed");suspend(level,e.endpointId);state(level).pending.values().removeIf(p->p.endpoint.equals(e.endpointId));
    }
    static void link(ServerLevel level,CouplerEndpoint e,ServerPlayer player){
        boolean unlink=connected(level,e);
        register(level,e);var s=state(level);var data=CouplerSavedData.get(level);
        if(unlink){s.pending.remove(player.getUUID());if(data.links.forEndpoint(e.endpointId)==null)player.displayClientMessage(Component.translatable("message.guidedrailways.coupler.not_connected"),true);else{disconnect(level,e.endpointId,"link_tool");player.displayClientMessage(Component.translatable("message.guidedrailways.coupler.disconnected"),true);}return;}
        if(data.links.forEndpoint(e.endpointId)!=null){player.displayClientMessage(Component.translatable("message.guidedrailways.coupler.already_connected_hint"),true);return;}
        var pending=s.pending.get(player.getUUID());
        if(pending==null||pending.expires<level.getGameTime()){
            if(body(level,e)==null)throw new IllegalArgumentException("message.guidedrailways.coupler.install");
            s.pending.put(player.getUUID(),new Pending(e.endpointId,level.getGameTime()+600));player.displayClientMessage(Component.translatable("message.guidedrailways.coupler.selected"),true);return;
        }
        if(pending.endpoint.equals(e.endpointId)){s.pending.remove(player.getUUID());player.displayClientMessage(Component.translatable("message.guidedrailways.coupler.cancelled"),true);return;}
        var first=s.endpoints.get(pending.endpoint);
        if(!loaded(level,first)){s.pending.remove(player.getUUID());throw new IllegalArgumentException("message.guidedrailways.coupler.unloaded");}
        if(!level.mayInteract(player,first.getBlockPos()))throw new IllegalArgumentException("message.guidedrailways.coupler.denied");
        connect(level,first,e);s.pending.remove(player.getUUID());player.displayClientMessage(Component.translatable(RailwayHardware.kind(e)==CouplerKind.STANDARD?"message.guidedrailways.coupler.connected":"message.guidedrailways.coupler.connecting"),true);
    }
    private static void connect(ServerLevel level,CouplerEndpoint a,CouplerEndpoint b){
        if(!CouplerKind.compatible(RailwayHardware.kind(a),RailwayHardware.kind(b)))throw new IllegalArgumentException("message.guidedrailways.coupler.type_mismatch");
        var data=CouplerSavedData.get(level);if(data.links.forEndpoint(a.endpointId)!=null||data.links.forEndpoint(b.endpointId)!=null)throw new IllegalArgumentException("message.guidedrailways.coupler.occupied");
        var ba=body(level,a);var bb=body(level,b);if(ba==null||bb==null)throw new IllegalArgumentException("message.guidedrailways.coupler.install_both");
        if(ba==bb)throw new IllegalArgumentException("message.guidedrailways.coupler.same_body");
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        if(!CouplerGeometry.slowEnough(pipe.getLinearVelocity(ba,new Vector3d()),pipe.getLinearVelocity(bb,new Vector3d()),pipe.getAngularVelocity(ba,new Vector3d()),pipe.getAngularVelocity(bb,new Vector3d())))throw new IllegalArgumentException("message.guidedrailways.coupler.too_fast");
        var geometry=engagement(level,a,b,ba,bb);var kind=RailwayHardware.kind(a);
        var socketA=socket(a);var socketB=socket(b);
        double length=CouplerMechanism.nominalLength(kind,CouplerGeometry.nominalLength(RailwayHardware.pivot(a).distance(RailwayHardware.tip(a)),RailwayHardware.pivot(b).distance(RailwayHardware.tip(b))));
        length=CouplerBufferSpacing.length(level,a,b,ba,bb,length);
        a.geometry(socketA.sub(blockOrigin(a)),localFrame(ba,geometry.frame()),length);
        b.geometry(socketB.sub(blockOrigin(b)),localFrame(bb,geometry.frame()),length);
        a.visualPeer(b);b.visualPeer(a);
        var link=new CouplingLinks.Link(UUID.randomUUID(),a.endpointId,b.endpointId);data.add(link);
        try{reconcile(level,link);if(CouplerMechanism.carries(kind,a.hardwareProgress(0))&&!state(level).live.containsKey(link.id()))throw new IllegalStateException("message.guidedrailways.coupler.failed");}
        catch(RuntimeException error){disconnect(level,a.endpointId,"creation_failed");throw error;}
    }
    private static CouplerGeometry.Engagement engagement(ServerLevel level,CouplerEndpoint a,CouplerEndpoint b,ServerSubLevel ba,ServerSubLevel bb){
        var geometry=CouplerGeometry.engage(VehiclePhysics.world(ba,head(a)),facing(ba,a),up(ba),VehiclePhysics.world(bb,head(b)),facing(bb,b),up(bb));
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();var kind=RailwayHardware.kind(a);var profile=CouplerMechanism.profile(kind);
        double gap=VehiclePhysics.world(ba,head(a)).distance(VehiclePhysics.world(bb,head(b)));
        if(gap>profile.reach())throw new IllegalArgumentException("message.guidedrailways.coupler.approach");
        if(kind!=CouplerKind.STANDARD){
            if(facing(ba,a).dot(facing(bb,b))>-.3)throw new IllegalArgumentException("message.guidedrailways.coupler.align");
            if(pipe.getLinearVelocity(ba,new Vector3d()).length()>profile.maxSpeed()||pipe.getLinearVelocity(bb,new Vector3d()).length()>profile.maxSpeed())throw new IllegalArgumentException("message.guidedrailways.coupler.too_fast");
        }
        return geometry;
    }
    static boolean connected(ServerLevel level,CouplerEndpoint e){return CouplerSavedData.get(level).links.forEndpoint(e.endpointId)!=null;}
    /** Trackside operation reuses exactly the same saved links and physics joints as a wrench. */
    static int trackOperation(ServerLevel level,ServerSubLevel ba,ServerSubLevel bb,boolean couple,boolean decouple,boolean execute){
        if(ba==null||bb==null||ba==bb)return 0;
        var s=state(level);var data=CouplerSavedData.get(level);var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        boolean stopped=CouplerGeometry.slowEnough(pipe.getLinearVelocity(ba,new Vector3d()),pipe.getLinearVelocity(bb,new Vector3d()),pipe.getAngularVelocity(ba,new Vector3d()),pipe.getAngularVelocity(bb,new Vector3d()));
        if(!stopped)return 0;
        var endsA=s.endpoints.values().stream().filter(e->loaded(level,e)&&body(level,e)==ba).sorted(Comparator.comparing(e->e.endpointId)).toList();
        var endsB=s.endpoints.values().stream().filter(e->loaded(level,e)&&body(level,e)==bb).sorted(Comparator.comparing(e->e.endpointId)).toList();
        CouplerEndpoint first=null,second=null;int mode=0;double best=Double.POSITIVE_INFINITY;
        boolean sameTrain=CargoStorage.consist(level,ba).bodies().containsKey(bb.getUniqueId());
        for(var a:endsA)for(var b:endsB){
            var link=data.links.forEndpoint(a.endpointId);boolean linked=link!=null&&(link.a().equals(b.endpointId)||link.b().equals(b.endpointId));
            boolean free=linked||CouplerKind.compatible(RailwayHardware.kind(a),RailwayHardware.kind(b))&&link==null&&data.links.forEndpoint(b.endpointId)==null;
            int candidate=RailCouplerMode.choose(linked,sameTrain,stopped,free,couple,decouple);if(candidate==0)continue;
            double distance=VehiclePhysics.world(ba,head(a)).distanceSquared(VehiclePhysics.world(bb,head(b)));
            if(!linked)try{engagement(level,a,b,ba,bb);}catch(IllegalArgumentException rejected){continue;}
            if(distance<best){best=distance;first=a;second=b;mode=candidate;}
        }
        if(mode==0||!execute)return mode;
        var affected=new HashSet<>(CargoStorage.consist(level,ba).bodies().keySet());affected.addAll(CargoStorage.consist(level,bb).bodies().keySet());
        if(mode==2)disconnect(level,first.endpointId,"trackside");else connect(level,first,second);
        CargoStorage.topologyChanged(level);ConsistRoutes.topologyChanged(level);DriverControls.topologyChanged(level,affected);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[RailwayCoupler] mode={} bodies={}/{} endpoints={}/{}",mode,ba.getUniqueId(),bb.getUniqueId(),first.endpointId,second.endpointId);
        return mode;
    }
    record BodyLink(ServerSubLevel a,ServerSubLevel b,Vector3d anchorA,Vector3d anchorB){}
    static List<BodyLink> bodyLinks(ServerLevel level){
        var s=STATES.get(level);if(s==null)return List.of();var result=new ArrayList<BodyLink>();
        for(var live:s.live.values())if(live.joint.isValid()&&!live.a.isRemoved()&&!live.b.isRemoved()&&loaded(level,live.visualA)&&loaded(level,live.visualB))
            result.add(new BodyLink(live.a,live.b,blockOrigin(live.visualA).add(live.visualA.anchor),blockOrigin(live.visualB).add(live.visualB.anchor)));
        return result;
    }
    static boolean blocksRelocation(ServerSubLevel carrier){
        var level=carrier.getLevel();var s=STATES.get(level);if(s==null)return false;
        for(var e:s.endpoints.values())if(loaded(level,e)&&body(level,e)==carrier&&connected(level,e))return true;return false;
    }
    static boolean cargoComplete(ServerLevel level,Set<UUID> bodies){
        var s=STATES.get(level);if(s==null)return true;
        for(var e:s.endpoints.values())if(loaded(level,e)){
            var b=body(level,e);if(b==null||!bodies.contains(b.getUniqueId()))continue;
            var link=CouplerSavedData.get(level).links.forEndpoint(e.endpointId);if(link==null)continue;
            var live=s.live.get(link.id());if(live==null||!live.joint.isValid()||!loaded(level,live.visualA)||!loaded(level,live.visualB)
                ||live.a.isRemoved()||live.b.isRemoved()||!bodies.contains(live.a.getUniqueId())||!bodies.contains(live.b.getUniqueId()))return false;
        }
        return true;
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
        if(!CouplerKind.compatible(RailwayHardware.kind(a),RailwayHardware.kind(b))){disconnect(level,a.endpointId,"incompatible_hardware");return;}
        var ba=body(level,a);var bb=body(level,b);if(ba==null||bb==null){close(s,link.id());return;}
        if(ba==bb){disconnect(level,link.a(),"merged_into_same_body");return;}
        var old=s.live.get(link.id());
        if(old!=null&&(!old.joint.isValid()||old.a!=ba||old.b!=bb||!old.pa.equals(a.getBlockPos())||!old.pb.equals(b.getBlockPos()))){close(s,link.id());old=null;}
        if(!a.hasGeometry||!b.hasGeometry){disconnect(level,link.a(),"missing_saved_geometry");return;}
        a.visualPeer(b);b.visualPeer(a);
        var kind=RailwayHardware.kind(a);double progress=Math.min(a.hardwareProgress(0),b.hardwareProgress(0));
        if(!CouplerMechanism.carries(kind,progress)){
            if(VehiclePhysics.world(ba,head(a)).distance(VehiclePhysics.world(bb,head(b)))>CouplerMechanism.profile(kind).reach()+.1)disconnect(level,a.endpointId,"coupling_interrupted");
            return;
        }
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        // Migrate older anchors/engagement-dependent lengths to standard drawbars.
        if(a.restLength<0||b.restLength<0||kind!=CouplerKind.STANDARD&&(a.mechanismVersion<2||b.mechanismVersion<2)){
            var pa=socket(a);var pb=socket(b);var wa=VehiclePhysics.world(ba,pa);var wb=VehiclePhysics.world(bb,pb);
            double length=CouplerMechanism.nominalLength(kind,CouplerGeometry.nominalLength(RailwayHardware.pivot(a).distance(RailwayHardware.tip(a)),RailwayHardware.pivot(b).distance(RailwayHardware.tip(b))));var frame=CouplerGeometry.distanceFrame(wa,wb);
            try{length=CouplerBufferSpacing.length(level,a,b,ba,bb,length);}catch(IllegalArgumentException impossible){disconnect(level,a.endpointId,"buffer_spacing_changed");return;}
            a.geometry(pa.sub(blockOrigin(a)),localFrame(ba,frame),length);b.geometry(pb.sub(blockOrigin(b)),localFrame(bb,frame),length);
            if(old!=null){close(s,link.id());old=null;}
        }
        var anchorA=blockOrigin(a).add(a.anchor);var anchorB=blockOrigin(b).add(b.anchor);
        var worldA=VehiclePhysics.world(ba,anchorA);var worldB=VehiclePhysics.world(bb,anchorB);
        var frame=CouplerGeometry.distanceFrame(worldA,worldB);var frameA=localFrame(ba,frame);var frameB=localFrame(bb,frame);
        if(old==null){
            double error=Math.abs(CouplerGeometry.extension(worldA,worldB,a.restLength));
            if(error>.35)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[Coupler] restoring stretched buffer link={} extension={} with capped spring",link.id(),error);
            GenericConstraintHandle joint=null;
            try{
                joint=pipe.addConstraint(ba,bb,new GenericConstraintConfiguration(anchorA,anchorB,frameA,frameB,EnumSet.noneOf(ConstraintJointAxis.class)));
                // The capped spring first takes up a loose coupling gap without teleporting.
                joint.setContactsEnabled(true);
                old=new Live(joint,ba,bb,a,b);tune(old);s.live.put(link.id(),old);s.warned.remove(link.id());
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[CouplerJoint] created link={} bodies={}/{} nominalStroke={} savedAnchorError={} mode=standard_length_buffer",link.id(),ba.getUniqueId(),bb.getUniqueId(),CouplerGeometry.HALF_STROKE*2,error);
            }catch(RuntimeException errorCreating){if(joint!=null&&joint.isValid())joint.remove();throw errorCreating;}
        }else{
            // Only radial distance is constrained; Y/Z and all rotations stay free.
            // Reorient each physics step, never aim back toward the engagement heading.
            old.joint.setFrame1(anchorA,frameA);old.joint.setFrame2(anchorB,frameB);tune(old);
        }
        if(!old.captured){
            var delta=new Vector3d(worldB).sub(worldA);double distance=delta.length();
            double radialSpeed=Double.POSITIVE_INFINITY;
            if(distance>1e-8){
                var poseA=pipe.readPose(ba,new Pose3d());var poseB=pipe.readPose(bb,new Pose3d());
                var va=pipe.getAngularVelocity(ba,new Vector3d()).cross(new Vector3d(worldA).sub(poseA.position())).add(pipe.getLinearVelocity(ba,new Vector3d()));
                var vb=pipe.getAngularVelocity(bb,new Vector3d()).cross(new Vector3d(worldB).sub(poseB.position())).add(pipe.getLinearVelocity(bb,new Vector3d()));
                radialSpeed=vb.sub(va).dot(delta.div(distance));
            }
            if((!CouplerMechanism.profile(kind).tensionOnly()||progress>=1)&&CouplerMechanism.canCapture(kind,distance,radialSpeed,a.restLength)){
                old.joint.setLimit(ConstraintJointAxis.LINEAR_X,CouplerMechanism.lower(kind,a.restLength),CouplerMechanism.upper(kind,a.restLength));
                old.captured=true;
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[Coupler] buffer captured link={} distance={} stroke={}",link.id(),distance,CouplerGeometry.HALF_STROKE*2);
            }
        }
        a.visualPeer(b);b.visualPeer(a);
        if(level.getGameTime()>=old.nextLog){
            old.nextLog=level.getGameTime()+20;
            var wa=VehiclePhysics.world(ba,blockOrigin(a).add(a.anchor));var wb=VehiclePhysics.world(bb,blockOrigin(b).add(b.anchor));
            if(level.players().stream().anyMatch(p->p.distanceToSqr(wa.x,wa.y,wa.z)<4096)){
                var frameWorld=new Quaterniond(frame);
                var delta=new Vector3d(wb).sub(wa).rotate(frameWorld.conjugate());
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[CouplerState] link={} bufferX={} transverseError={} supportedMassA={} supportedMassB={} joint={} socketDistance={} restLength={} swivelDeg={}",link.id(),delta.x-a.restLength,Math.hypot(delta.y,delta.z),old.ma,old.mb,old.joint.isValid(),wa.distance(wb),a.restLength,Math.toDegrees(Math.acos(Math.clamp(-facing(ba,a).dot(facing(bb,b)),-1,1))));
            }
        }
    }
    private static void tune(Live live){
        double ma=VehiclePhysics.coupledMass(live.a),mb=VehiclePhysics.coupledMass(live.b);
        var kind=RailwayHardware.kind(live.visualA);var profile=CouplerMechanism.profile(kind);
        if(!profile.tensionOnly()&&Math.abs(live.ma-ma)<.01&&Math.abs(live.mb-mb)<.01)return;
        var spring=CouplerGeometry.spring(ma,mb);double progress=Math.min(live.visualA.hardwareProgress(0),live.visualB.hardwareProgress(0));
        double target=CouplerMechanism.workingLength(kind,live.visualA.restLength,progress);
        double distance=VehiclePhysics.world(live.a,blockOrigin(live.visualA).add(live.visualA.anchor)).distance(VehiclePhysics.world(live.b,blockOrigin(live.visualB).add(live.visualB.anchor)));
        boolean slack=profile.tensionOnly()&&distance<=target;
        double damping=spring.damping();
        if(profile.tensionOnly()&&!slack){
            var pipe=SubLevelPhysicsSystem.get(live.a.getLevel()).getPipeline();var wa=VehiclePhysics.world(live.a,blockOrigin(live.visualA).add(live.visualA.anchor));var wb=VehiclePhysics.world(live.b,blockOrigin(live.visualB).add(live.visualB.anchor));
            var axis=new Vector3d(wb).sub(wa);if(axis.lengthSquared()>1e-12){
                var pa=pipe.readPose(live.a,new Pose3d());var pb=pipe.readPose(live.b,new Pose3d());
                var va=pipe.getAngularVelocity(live.a,new Vector3d()).cross(new Vector3d(wa).sub(pa.position())).add(pipe.getLinearVelocity(live.a,new Vector3d()));
                var vb=pipe.getAngularVelocity(live.b,new Vector3d()).cross(new Vector3d(wb).sub(pb.position())).add(pipe.getLinearVelocity(live.b,new Vector3d()));
                double radial=vb.sub(va).dot(axis.normalize());damping=CouplerMechanism.tensionDamping(distance-target,radial,spring.stiffness(),damping);
            }
        }
        live.joint.setMotor(ConstraintJointAxis.LINEAR_X,target,slack?0:spring.stiffness(),slack?0:damping,true,slack?0:spring.maxForce());live.ma=ma;live.mb=mb;
    }
    static void beforeMove(ServerLevel from,ServerLevel to,BlockPos oldPos,BlockPos newPos){
        var e=CouplerEndpoint.get(from.getBlockEntity(oldPos));if(e==null)return;
        if(from!=to)disconnect(from,e.endpointId,"dimension_changed");
        var b=body(from,e);Vector3d worldAnchor=null;Quaterniond worldFrame=null;
        if(b!=null&&e.hasGeometry){worldAnchor=VehiclePhysics.world(b,blockOrigin(e).add(e.anchor));worldFrame=new Quaterniond(SubLevelPhysicsSystem.get(from).getPipeline().readPose(b,new Pose3d()).orientation()).mul(e.frame);}
        var source=GlobalPos.of(from.dimension(),oldPos.immutable());MOVING.put(source,new Moving(e.endpointId,from.getGameTime()+200));
        TRANSFERS.put(GlobalPos.of(to.dimension(),newPos.immutable()),new Transfer(e.endpointId,worldAnchor,worldFrame,e.hasGeometry&&worldAnchor!=null,e.restLength,source,to.getGameTime()+200));suspend(from,e.endpointId);
    }
    static void afterMove(ServerLevel to,BlockPos pos){var e=CouplerEndpoint.get(to.getBlockEntity(pos));if(e!=null)register(to,e);}
    private static boolean restore(ServerLevel level,CouplerEndpoint e){
        var key=GlobalPos.of(level.dimension(),e.getBlockPos());var transfer=TRANSFERS.get(key);if(transfer==null)return true;
        e.endpointId=transfer.endpoint;
        if(transfer.geometry){var b=body(level,e);if(b==null||b.getMassTracker().getMass()<=0)return false;e.geometry(local(b,transfer.anchor).sub(blockOrigin(e)),localFrame(b,transfer.frame),transfer.length);}
        else{e.hasGeometry=false;e.setChanged();}
        TRANSFERS.remove(key);MOVING.put(transfer.source,new Moving(e.endpointId,level.getGameTime()+5));return true;
    }
    static void unload(ServerLevel level){
        var s=STATES.remove(level);if(s!=null)s.live.values().forEach(live->live.close(false));
        TRANSFERS.entrySet().removeIf(e->e.getKey().dimension().equals(level.dimension())||e.getValue().source.dimension().equals(level.dimension()));
        MOVING.keySet().removeIf(p->p.dimension().equals(level.dimension()));
    }
}
