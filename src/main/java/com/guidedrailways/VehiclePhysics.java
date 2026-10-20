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
    static final double RIDE_HEIGHT=BogieGeometry.RIDE_HEIGHT,HALF_WHEELBASE=1.0,PLATE_OFFSET=BogieGeometry.PLATE_OFFSET;
    /** Mount plate underside, flush with the top of its block so blocks above sit on it. */
    private static final double PLATE_BOTTOM=BogieGeometry.MOUNT_UNDERSIDE;
    private static final Quaterniond ANCHOR_ROT=new Quaterniond().rotateY(-Math.PI/2);
    private static final Map<ServerLevel,Map<BlockPos,MountEntity>> INPUTS=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Unit>> ACTIVE=new WeakHashMap<>();
    static final class Unit {
        final MountEntity mount;final ServerSubLevel carrier,bogie;final Vector3d localCenter;
        DriverConsoleEntity driver;AutomaticTrains.Control automatic;
        GenericConstraintHandle pivot;final GenericConstraintHandle[] guides=new GenericConstraintHandle[2];
        final TrackNetwork.Path[] routes=new TrackNetwork.Path[2];
        final TrackNetwork.Sample[] samples=new TrackNetwork.Sample[2];
        final DerailmentModel.Exposure exposure=new DerailmentModel.Exposure();
        double mass,speed,curveRisk,lateralSpeed;long lastSeen,nextLog,derailGraceUntil;boolean detached,reverseTravel;int supports=1;
        Unit(MountEntity mount,ServerSubLevel carrier,ServerSubLevel bogie){var variant=BogieBlock.variant(bogie.getLevel().getBlockState(bogie.getPlot().getCenterBlock()));if(mount.variant!=variant){mount.variant=variant;mount.setChanged();}this.mount=mount;this.carrier=carrier;this.bogie=bogie;localCenter=center(bogie);var tag=bogie.getUserDataTag();detached=tag!=null&&tag.getBoolean("GuidedDerailed");}
        Vector3d axle(int i){return new Vector3d(localCenter).add(0,-mount.variant.ride(mount.gauge),i==0?mount.variant.half:-mount.variant.half);}
        void clearGuides(){for(int i=0;i<2;i++){if(guides[i]!=null&&guides[i].isValid())guides[i].remove();guides[i]=null;}}
        void close(){clearGuides();if(pivot!=null&&pivot.isValid())pivot.remove();}
    }
    private VehiclePhysics(){}
    static Collection<Unit> cargoUnits(ServerLevel level){return List.copyOf(ACTIVE.getOrDefault(level,Map.of()).values());}
    static Vector3d center(ServerSubLevel body){var p=body.getPlot().getCenterBlock();return new Vector3d(p.getX()+.5,p.getY()+.5,p.getZ()+.5);}
    static Vector3d world(ServerSubLevel body,Vector3dc local){var pose=SubLevelPhysicsSystem.get(body.getLevel()).getPipeline().readPose(body,new Pose3d());return new Vector3d(local).sub(body.logicalPose().rotationPoint()).rotate(pose.orientation()).add(pose.position());}
    static double coupledMass(ServerSubLevel carrier){
        double mass=carrier.getMassTracker().getMass();for(var u:ACTIVE.getOrDefault(carrier.getLevel(),Map.of()).values())if(u.carrier==carrier&&!u.bogie.isRemoved())mass+=u.bogie.getMassTracker().getMass();return mass;
    }
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
        DriverControls.refresh(level,units.values());
        PerfCounters.report(level,units.size());
        var counts=new HashMap<UUID,Integer>();for(var u:units.values())counts.merge(u.carrier.getUniqueId(),1,Integer::sum);
        for(var u:units.values()){
            int supports=counts.get(u.carrier.getUniqueId());
            if(u.supports!=supports){if(u.pivot!=null&&u.pivot.isValid())u.pivot.remove();u.supports=supports;connectPivot(u);}
            u.mass=u.carrier.getMassTracker().getMass()/supports+u.bogie.getMassTracker().getMass();u.mount.setLoad(u.mass);
            if(level.getBlockEntity(u.bogie.getPlot().getCenterBlock()) instanceof BogieEntity visual){visual.speed=u.speed;visual.distance+=u.speed/20;if(level.getGameTime()%2==0)visual.sendData();}
            if(level.getGameTime()>=u.nextLog){u.nextLog=level.getGameTime()+20;
                var pos=world(u.bogie,u.localCenter);if(level.players().stream().anyMatch(p->p.distanceToSqr(pos.x,pos.y,pos.z)<4096)){
                    var pipeline=system.getPipeline();
                    double verticalSpeed=pipeline.getLinearVelocity(u.bogie,new Vector3d()).y,angularSpeed=pipeline.getAngularVelocity(u.bogie,new Vector3d()).length();
                    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuidedBogie] body={} speed={} target={} inputRpm={} supportedMass={} brakes={} guided={} travel={} verticalSpeed={} angularSpeedRad={} curveRisk={} lateralSpeed={} carrier={} mounts={} pivot={} anchors={}",u.bogie.getUniqueId(),u.speed,u.driver==null?u.mount.getSpeed()*TrainConfig.speedPerRpm():driverRpm(u,new Vector3d(0,0,1).rotate(system.getPipeline().readPose(u.bogie,new Pose3d()).orientation())),u.mount.getSpeed(),u.mass,u.mount.braking||(u.automatic!=null?u.automatic.hold()||u.automatic.emergency()||u.automatic.notch()<0:u.driver!=null&&(!u.driver.enabled||u.driver.emergency||u.driver.notch<0||u.driver.powered==0)),!u.detached,u.reverseTravel?"reverse":"forward",verticalSpeed,angularSpeed,u.curveRisk,u.lateralSpeed,u.carrier.getUniqueId(),counts.get(u.carrier.getUniqueId()),TrainConfig.DETAILED_LOGGING.get()?pivotSummary(u):"off",TrainConfig.DETAILED_LOGGING.get()?anchorSummary(u):"off");
                }
            }
        }
    }
    private static String pivotSummary(Unit u){
        var pipe=SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
        var upper=new Vector3d(u.mount.getBlockPos().getX()+.5,u.mount.getBlockPos().getY()+PLATE_BOTTOM,u.mount.getBlockPos().getZ()+.5);
        var lower=new Vector3d(u.localCenter).add(0,u.mount.pivotHeight(),0);
        var a=pipe.readPose(u.carrier,new Pose3d());var b=pipe.readPose(u.bogie,new Pose3d());
        var relative=new Quaterniond(b.orientation()).conjugate().mul(a.orientation()).mul(u.mount.pivotReference);
        var rate=pipe.getAngularVelocity(u.carrier,new Vector3d()).sub(pipe.getAngularVelocity(u.bogie,new Vector3d())).rotate(new Quaterniond(b.orientation()).conjugate());
        var up=new Vector3d(0,1,0).rotate(relative);
        double tilt=Math.toDegrees(Math.acos(Math.clamp(up.y,-1,1)));
        var neutral=new Quaterniond(a.orientation()).mul(u.mount.pivotReference);
        var yawForward=new Vector3d(0,0,1).rotate(b.orientation()).rotate(neutral.conjugate());
        double yawError=Math.toDegrees(Math.atan2(yawForward.x,yawForward.z));
        return String.format(java.util.Locale.ROOT,"anchorError=%.5f,tiltDeg=%.3f,yawErrorDeg=%.3f,yawRateDeg=%.3f,pitchRateDeg=%.3f,rollRateDeg=%.3f,mode=%s",world(u.carrier,upper).distance(world(u.bogie,lower)),tilt,yawError,Math.toDegrees(rate.y),Math.toDegrees(rate.x),Math.toDegrees(rate.z),u.supports==1?"stable":"articulated");
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
        var upper=new Vector3d(u.mount.getBlockPos().getX()+.5,u.mount.getBlockPos().getY()+PLATE_BOTTOM,u.mount.getBlockPos().getZ()+.5);
        var lower=new Vector3d(u.localCenter).add(0,u.mount.pivotHeight(),0);
        var locks=EnumSet.of(ConstraintJointAxis.LINEAR_X,ConstraintJointAxis.LINEAR_Y,ConstraintJointAxis.LINEAR_Z,ConstraintJointAxis.ANGULAR_Z);
        if(u.supports==1)locks.add(ConstraintJointAxis.ANGULAR_X);
        if(!u.mount.pivotReferenceKnown){
            var carrier=pipe.readPose(u.carrier,new Pose3d());var bogie=pipe.readPose(u.bogie,new Pose3d());
            u.mount.pivotReference.set(BogieYawModel.migrate(carrier.orientation(),bogie.orientation()));u.mount.pivotReferenceKnown=true;u.mount.setChanged();
        }
        u.pivot=pipe.addConstraint(u.carrier,u.bogie,new GenericConstraintConfiguration(upper,lower,u.mount.pivotReference,new Quaterniond(),locks));
        u.pivot.setContactsEnabled(false);
        // A stable bearing replaces the prototype's spring-supported empty plate.
        // Weak yaw centering, with damping and a small torque cap; yaw remains unlocked.
        var spring=BogieYawModel.spring(u.supports);
        u.pivot.setMotor(ConstraintJointAxis.ANGULAR_Y,0,spring.stiffness(),spring.damping(),true,spring.maxTorque());
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
        BogiePairContacts.step(physics,ACTIVE.getOrDefault(level,Map.of()).values(),dt);
        for(var plan:ConsistRoutes.prepare(level,ACTIVE.getOrDefault(level,Map.of()).values())){
            var u=plan.unit();
            if(u.carrier.isRemoved()||u.bogie.isRemoved()||u.mount.isRemoved()||u.lastSeen+2<level.getGameTime())continue;
            var pose=pipe.readPose(u.bogie,new Pose3d());var heading=new Vector3d(0,0,1).rotate(pose.orientation());
            if(u.detached){u.speed=pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(heading);continue;}
            // Leading axle steers at junctions (Create TravellingPoint.steer); trailing axle follows it.
            u.reverseTravel=BranchSelect.reverseTravel(pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(heading),driverRpm(u,heading),u.reverseTravel);
            boolean reverse=u.reverseTravel;
            int lead=reverse?1:0,trail=1-lead;
            double approachSpeed=Math.abs(pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(heading));
            double nose=PhysicalSignals.nose(u,world(u.bogie,u.axle(lead)),new Vector3d(heading).mul(reverse?-1:1))+.25;
            double switchRoom=TrackNetwork.switchDistance(level,u.samples[lead],heading,reverse,plan.axle(lead),approachSpeed*approachSpeed/(2*TrainConfig.BRAKING.get())+nose+8,nose);
            switchRoom=plan.axle(lead).limit(switchRoom);
            var atLead=world(u.bogie,u.axle(lead));
            u.samples[lead]=TrackNetwork.follow(level,atLead,heading,u.samples[lead],null,.8,reverse,plan.axle(lead));
            plan.axle(lead).followed(u.samples[lead]);
            if(u.samples[lead]==null){release(u,"track_missing_or_out_of_range");continue;}
            var atTrail=world(u.bogie,u.axle(trail));
            u.samples[trail]=TrackNetwork.follow(level,atTrail,heading,u.samples[trail],u.samples[lead],.8,reverse,plan.axle(trail));
            if(u.samples[trail]==null){release(u,"track_missing_or_out_of_range");continue;}
            if(TrainConfig.DERAILMENT.get()&&level.getGameTime()>=u.derailGraceUntil&&checkDerailment(u,physics,dt))continue;
            u.speed=0;
            for(int i=0;i<2;i++){
                var sample=u.samples[i];u.routes[i]=sample.path();var q=sample.orientation();
                if(u.guides[i]==null||!u.guides[i].isValid()){
                    // Two point guides already constrain pitch/yaw. A single roll lock is sufficient.
                    var locks=EnumSet.of(ConstraintJointAxis.LINEAR_Y,ConstraintJointAxis.LINEAR_Z);
                    if(i==0)locks.add(ConstraintJointAxis.ANGULAR_X);
                    u.guides[i]=pipe.addConstraint(null,u.bogie,new GenericConstraintConfiguration(sample.position(),u.axle(i),q,ANCHOR_ROT,locks));
                    u.guides[i].setContactsEnabled(false);
                }else u.guides[i].setFrame1(sample.position(),q);
                // Longitudinal translation is not locked: the solver never sets the vehicle's forward position.
                double speed=pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(sample.tangent());u.speed+=speed*.5;
                double force;
                if(u.automatic!=null){
                    var control=u.automatic;double ceiling=control.sign()*control.ceiling();
                    boolean hold=control.hold()||control.owner().isRemoved()||u.mount.braking||control.navigation()!=null&&control.navigation().routeRetry;
                    if(control.navigation()==null){
                        ceiling=Math.copySign(control.owner().manualCeiling(control.notch(),Math.abs(ceiling)),ceiling);
                        force=DriverModel.manualForce(speed,ceiling,u.mount.getSpeed(),u.mass,control.notch(),control.emergency(),hold,TrainConfig.FORCE_PER_RPM.get(),TrainConfig.ACCELERATION.get(),TrainConfig.BRAKING.get(),TrainConfig.COASTING.get(),dt);
                    }else force=DriverModel.force(speed,ceiling,u.mount.getSpeed(),u.mass,control.notch(),control.emergency(),hold,TrainConfig.FORCE_PER_RPM.get(),TrainConfig.ACCELERATION.get(),TrainConfig.BRAKING.get(),TrainConfig.COASTING.get(),dt);
                }else if(u.driver!=null){
                    var driver=u.driver;
                    boolean safe=driver.enabled&&!driver.isRemoved()&&DriverControls.body(driver)==u.carrier;
                    boolean hold=!safe||u.mount.braking||driver.powered==0;
                    double ceiling=driver.direction*driver.manualCeiling(driver.notch,driver.available)*driverSign(u,sample.tangent());
                    force=DriverModel.manualForce(speed,ceiling,u.mount.getSpeed(),u.mass,driver.notch,driver.emergency,hold,TrainConfig.FORCE_PER_RPM.get(),TrainConfig.ACCELERATION.get(),TrainConfig.BRAKING.get(),TrainConfig.COASTING.get(),dt);
                }else{
                    force=DriveModel.force(speed,u.mount.getSpeed(),u.mass,u.mount.braking,TrainConfig.speedPerRpm(),TrainConfig.FORCE_PER_RPM.get(),TrainConfig.ACCELERATION.get(),TrainConfig.BRAKING.get());
                    if(!u.mount.braking&&Math.abs(u.mount.getSpeed())<.001)force+=DriveModel.coastForce(speed,u.mass,TrainConfig.COASTING.get(),dt);
                }
                force=RailwayStopModel.force(speed,force,switchRoom,u.mass,TrainConfig.BRAKING.get(),dt);
                var impulse=new Vector3d(sample.tangent()).mul(force*dt*.5).rotate(new Quaterniond(pose.orientation()).conjugate());
                // Apply traction at the frame centre height, with front/rear positions distributed along its length.
                var point=new Vector3d(u.localCenter).add(0,0,i==0?u.mount.variant.half:-u.mount.variant.half);
                pipe.applyImpulse(u.bogie,point,impulse);
            }
        }
    }
    private static double driverSign(Unit u,Vector3dc heading){
        var f=u.driver.getBlockState().getValue(DriverConsoleBlock.FACING);
        var q=SubLevelPhysicsSystem.get(u.carrier.getLevel()).getPipeline().readPose(u.carrier,new Pose3d()).orientation();
        return new Vector3d(f.getStepX(),0,f.getStepZ()).rotate(q).dot(heading)<0?-1:1;
    }
    static double driverRpm(Unit u,Vector3dc heading){
        if(u.automatic!=null){var c=u.automatic;return !c.hold()&&!c.emergency()&&c.notch()>0?c.sign()*(c.navigation()==null?c.owner().manualCeiling(c.notch(),c.ceiling()):c.ceiling()):0;}
        return u.driver==null?u.mount.getSpeed():u.driver.enabled&&!u.driver.emergency&&u.driver.notch>0?u.driver.direction*u.driver.manualCeiling(u.driver.notch,u.driver.available)*driverSign(u,heading):0;
    }
    static void consoleRemoved(DriverConsoleEntity console){
        for(var u:ACTIVE.getOrDefault(console.getLevel(),Map.of()).values())if(u.driver==console&&!u.carrier.isRemoved()&&!u.mount.isRemoved()
                &&console.getLevel().hasChunkAt(u.mount.getBlockPos())&&console.getLevel().getBlockEntity(u.mount.getBlockPos())==u.mount){u.mount.braking=true;u.mount.setChanged();u.mount.sendData();}
    }
    static void releaseMountBrakes(DriverConsoleEntity console){
        for(var u:ACTIVE.getOrDefault(console.getLevel(),Map.of()).values())if(u.driver==console||u.automatic!=null&&u.automatic.owner()==console){u.mount.braking=false;u.mount.setChanged();u.mount.sendData();}
    }
    private static void release(Unit u,String reason){
        // Keep current velocity and the carrier bearing; remove only world-to-track constraints.
        var anchors=anchorSummary(u);u.clearGuides();u.detached=true;
        var tag=u.bogie.getUserDataTag();if(tag==null)tag=new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("GuidedDerailed",true);u.bogie.setUserDataTag(tag);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuideReleased] body={} reason={} speed={} curveRisk={} lateralSpeed={} curveSeconds={} impactSeconds={} anchors={}",u.bogie.getUniqueId(),reason,u.speed,u.curveRisk,u.lateralSpeed,u.exposure.curveSeconds,u.exposure.impactSeconds,anchors);
    }
    private record Relocate(UUID bogie,long expires){}
    private static final Map<UUID,Relocate> RELOCATING=new HashMap<>();
    /** Wrench a derailed vehicle, then wrench the destination track. Same gesture as Create's train relocator. */
    static void beginRelocate(net.minecraft.server.level.ServerPlayer player,Unit u){
        var level=u.bogie.getLevel();var pipeline=SubLevelPhysicsSystem.get(level).getPipeline();
        if(pipeline.getLinearVelocity(u.carrier,new Vector3d()).length()>.25||pipeline.getLinearVelocity(u.bogie,new Vector3d()).length()>.25){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.stop_select"),true);return;}
        var existing=RELOCATING.get(player.getUUID());
        if(existing!=null&&existing.bogie.equals(u.bogie.getUniqueId())&&existing.expires>=level.getGameTime()){
            RELOCATING.remove(player.getUUID());
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.cancelled"),true);
            return;
        }
        RELOCATING.put(player.getUUID(),new Relocate(u.bogie.getUniqueId(),level.getGameTime()+600));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.selected"),true);
    }
    static boolean hasRelocation(net.minecraft.server.level.ServerPlayer player){return RELOCATING.containsKey(player.getUUID());}
    /** @return true when this click was a relocate attempt and should not reach the track */
    static boolean relocateClick(net.minecraft.server.level.ServerPlayer player){
        var level=player.serverLevel();
        var pending=RELOCATING.get(player.getUUID());if(pending==null)return false;
        if(pending.expires<level.getGameTime()){RELOCATING.remove(player.getUUID());player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.expired"),true);return true;}
        var unit=ACTIVE.getOrDefault(level,Map.of()).get(pending.bogie);
        if(unit==null){RELOCATING.remove(player.getUUID());player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.missing_bogie"),true);return true;}
        var sample=TrackNetwork.pick(player,unit.mount.gauge);
        if(sample==null){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.aim"),true);return true;}
        var targetPos=BlockPos.containing(sample.position().x,sample.position().y,sample.position().z);
        if(!player.mayBuild()||!level.mayInteract(player,targetPos)){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.denied"),true);return true;}
        try{reraileCarriage(unit,sample);RELOCATING.remove(player.getUUID());player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.completed"),true);}
        catch(IllegalArgumentException|IllegalStateException e){
            player.displayClientMessage(UserText.error(e),true);
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[RailPlacement] action=rerail_rejected reason={}",e.getMessage());
        }catch(RuntimeException e){
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[RailPlacement] rerail failed",e);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.failed"),true);
        }
        return true;
    }
    /** Place every bogie on the same carrier onto the track around the clicked sample. */
    static void reraileCarriage(Unit selected,TrackNetwork.Sample sample){
        if(selected.carrier.isRemoved()||selected.bogie.isRemoved()||selected.mount.isRemoved())throw new IllegalStateException("message.guidedrailways.rail.missing_body");
        if(CouplerConnections.blocksRelocation(selected.carrier))throw new IllegalArgumentException("message.guidedrailways.rail.uncouple");
        var level=selected.bogie.getLevel();var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        var units=new ArrayList<Unit>();
        for(var u:ACTIVE.getOrDefault(level,Map.of()).values())if(u.carrier==selected.carrier&&!u.bogie.isRemoved()&&!u.mount.isRemoved())units.add(u);
        if(units.isEmpty())units.add(selected);
        for(var mount:INPUTS.getOrDefault(level,Map.of()).values())
            if(mount.bogieId!=null&&SableCompanion.INSTANCE.getContaining(level,mount.getBlockPos())==selected.carrier
                &&units.stream().noneMatch(u->u.mount==mount))throw new IllegalStateException("message.guidedrailways.rail.changed");
        units.sort(Comparator.comparing(u->u.bogie.getUniqueId()));
        int selectedIndex=units.indexOf(selected);if(selectedIndex<0)throw new IllegalStateException("message.guidedrailways.rail.changed");
        var carrierPose=pipe.readPose(selected.carrier,new Pose3d());var seats=new ArrayList<Vector3d>();
        for(var u:units)seats.add(new Vector3d(u.mount.getBlockPos().getX()+.5,u.mount.getBlockPos().getY()+PLATE_BOTTOM,u.mount.getBlockPos().getZ()+.5).sub(u.carrier.logicalPose().rotationPoint()));
        var localForward=new Vector3d(0,0,1).rotate(pipe.readPose(selected.bogie,new Pose3d()).orientation()).rotate(new Quaterniond(carrierPose.orientation()).conjugate());
        localForward.y=0;if(localForward.lengthSquared()<1e-8)localForward.set(0,0,1);else localForward.normalize();
        double extent=4;for(int i=0;i<seats.size();i++)extent=Math.max(extent,seats.get(i).distance(seats.get(selectedIndex))*1.8+units.get(i).mount.variant.half*2+4);
        if(extent>256)throw new IllegalArgumentException("message.guidedrailways.rail.too_long");
        var route=TrackNetwork.placementRoute(level,sample,extent,units.stream().map(u->u.mount.gauge).collect(java.util.stream.Collectors.toSet()));
        var plan=RailPlacement.carriage(route,seats,selectedIndex,localForward,units.stream().map(u->new RailPlacement.Dimensions(u.mount.variant.ride(u.mount.gauge),u.mount.variant.half,u.mount.pivotHeight())).toList());
        if(pipe.getLinearVelocity(selected.carrier,new Vector3d()).length()>.25)throw new IllegalArgumentException("message.guidedrailways.rail.stop");
        record Move(ServerSubLevel body,Vector3d position,Quaterniond rotation,Pose3d before,Vector3d velocity,Vector3d angular){}
        var moves=new ArrayList<Move>();
        moves.add(new Move(selected.carrier,plan.position(),plan.rotation(),carrierPose,pipe.getLinearVelocity(selected.carrier,new Vector3d()),pipe.getAngularVelocity(selected.carrier,new Vector3d())));
        for(int i=0;i<units.size();i++){
            var u=units.get(i);var fit=plan.bogies().get(i);checkPlacementSpace(level,fit.origin(),fit.rotation(),u.mount.variant,u.mount.gauge,units);
            var target=new Vector3d(u.bogie.logicalPose().rotationPoint()).sub(center(u.bogie)).rotate(fit.rotation()).add(fit.origin());
            moves.add(new Move(u.bogie,target,fit.rotation(),pipe.readPose(u.bogie,new Pose3d()),pipe.getLinearVelocity(u.bogie,new Vector3d()),pipe.getAngularVelocity(u.bogie,new Vector3d())));
        }
        MoveBatch.commit(moves,m->{
            if(m.body.isRemoved()||!m.position.isFinite()||!m.rotation.isFinite())throw new IllegalStateException("message.guidedrailways.body.changed");
            if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(m.position.x,m.position.y,m.position.z)))throw new IllegalArgumentException("message.guidedrailways.placement.border");
        },m->{
            pipe.teleport(m.body,m.position,m.rotation);pipe.resetVelocity(m.body);
            m.body.updateLastPose();m.body.updateBoundingBox();
        },m->{
            pipe.teleport(m.body,m.before.position(),m.before.orientation());pipe.resetVelocity(m.body);
            pipe.addLinearAndAngularVelocity(m.body,m.velocity,m.angular);
            m.body.updateLastPose();m.body.updateBoundingBox();
        });
        for(int i=0;i<units.size();i++){
            var unit=units.get(i);
            if(unit.mount.heightVersion<BogieGeometry.MOUNT_HEIGHT_VERSION){
                if(unit.pivot!=null&&unit.pivot.isValid())unit.pivot.remove();
                unit.mount.heightVersion=BogieGeometry.MOUNT_HEIGHT_VERSION;unit.mount.setChanged();unit.mount.sendData();connectPivot(unit);
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[PlatformHeight] carrier={} mount={} version={} raised={}",unit.carrier.getUniqueId(),unit.mount.getBlockPos(),unit.mount.heightVersion,BogieGeometry.PLATFORM_RAISE);
            }
            finishReraile(unit);seedPlacement(unit,plan.bogies().get(i),route);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuideRerailed] carrier={} bogies={} curve={} anchor={}",selected.carrier.getUniqueId(),units.size(),sample.path().edge().isTurn(),sample.position());
    }
    private static void finishReraile(Unit u){
        u.clearGuides();u.detached=false;u.exposure.curveSeconds=0;u.exposure.impactSeconds=0;
        u.samples[0]=null;u.samples[1]=null;u.routes[0]=null;u.routes[1]=null;
        u.speed=0;u.curveRisk=0;u.lateralSpeed=0;u.reverseTravel=u.mount.getSpeed()<0;
        u.derailGraceUntil=u.bogie.getLevel().getGameTime()+40;
        var tag=u.bogie.getUserDataTag();if(tag==null)tag=new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("GuidedDerailed",false);u.bogie.setUserDataTag(tag);
    }
    private static boolean checkDerailment(Unit u,SubLevelPhysicsSystem physics,double dt){
        var pipe=physics.getPipeline();var velocity=pipe.getLinearVelocity(u.bogie,new Vector3d());
        var angular=pipe.getAngularVelocity(u.bogie,new Vector3d());
        var bogieCom=world(u.bogie,u.bogie.logicalPose().rotationPoint());
        var carrierCom=world(u.carrier,u.carrier.logicalPose().rotationPoint());
        double lowerMass=u.bogie.getMassTracker().getMass(),upperMass=u.carrier.getMassTracker().getMass()/u.supports;
        var supportedCom=new Vector3d(bogieCom).mul(lowerMass).fma(upperMass,carrierCom).div(Math.max(.001,lowerMass+upperMass));
        u.curveRisk=0;u.lateralSpeed=0;u.speed=0;
        for(int i=0;i<2;i++){
            var sample=u.samples[i];double speed=velocity.dot(sample.tangent());u.speed+=speed*.5;
            double height=new Vector3d(supportedCom).sub(sample.position()).dot(sample.up());
            u.curveRisk=Math.max(u.curveRisk,DerailmentModel.curveRisk(speed,sample.lateralCurvature(),height,TrainConfig.DERAIL_LATERAL.get()));
            // Use axle point velocity, including yaw, so normal turning is not a side impact.
            var lever=world(u.bogie,u.axle(i)).sub(bogieCom);
            var pointVelocity=new Vector3d(angular).cross(lever).add(velocity);
            var side=new Vector3d(sample.tangent()).cross(sample.up()).normalize();
            u.lateralSpeed=Math.max(u.lateralSpeed,Math.abs(pointVelocity.dot(side)));
        }
        var reason=u.exposure.update(u.curveRisk,u.lateralSpeed,TrainConfig.DERAIL_IMPACT.get(),TrainConfig.DERAIL_DELAY.get(),dt);
        if(reason==null)return false;release(u,reason);return true;
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
        if(!player.getAbilities().instabuild){var stack=Content.bogieItem();if(!player.getInventory().add(stack))player.drop(stack,false);}
    }
    static void place(ServerLevel level,UseOnContext context){
        var player=(net.minecraft.server.level.ServerPlayer)context.getPlayer();
        if(SubLevelPhysicsSystem.get(level)==null)throw new IllegalStateException("message.guidedrailways.placement.no_physics");
        var carrierBody=SableCompanion.INSTANCE.getContaining(level,context.getClickedPos());
        if(carrierBody instanceof ServerSubLevel carrier){installBelow(level,context,carrier);return;}
        placeAimed(player);
    }
    /** Shared by block-use and air-use: the server picks the visible rail, including Bezier spans. */
    static void placeAimed(net.minecraft.server.level.ServerPlayer player){
        var level=player.serverLevel();
        if(!player.mayBuild())throw new IllegalArgumentException("message.guidedrailways.placement.denied");
        if(SubLevelPhysicsSystem.get(level)==null)throw new IllegalStateException("message.guidedrailways.placement.no_physics");
        var selection=PhysicalBogeySelection.selected(player);
        var sample=TrackNetwork.pick(player,null);
        if(sample==null)throw new IllegalArgumentException("message.guidedrailways.rail.aim");
        if(selection.gauge()!=null&&!sample.path().supports(selection.gauge()))throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
        var gauge=selection.gauge()==null?RailGauge.forTrack(sample.path().edge().getTrackMaterial().trackType.id.toString()):selection.gauge();
        if(selection.gauge()==null)selection=new PhysicalBogeySelection.Selection(gauge,BogeyVariant.defaultFor(gauge),selection.data());
        var pos=BlockPos.containing(sample.position().x,sample.position().y,sample.position().z);
        if(!level.mayInteract(player,pos))throw new IllegalArgumentException("message.guidedrailways.placement.track_denied");
        var route=TrackNetwork.placementRoute(level,sample,Math.max(4,selection.variant().half*2+2),Set.of(gauge));
        var fit=RailPlacement.bogie(route,0,selection.variant().ride(gauge),selection.variant().half,selection.variant().pivotHeight(BogieGeometry.MOUNT_HEIGHT_VERSION,gauge));checkPlacementSpace(level,fit.origin(),fit.rotation(),selection.variant(),gauge,List.of());
        ServerSubLevel bogie=null,carrier=null;
        try{
            bogie=createBody(level,Content.BOGIE.get().defaultBlockState().setValue(BogieBlock.GAUGE,gauge.ordinal()).setValue(BogieBlock.STYLE,selection.variant().ordinal()),fit.origin(),fit.rotation());
            applyBogeySelection(bogie,selection);
            var upperOrigin=new Vector3d(fit.origin()).add(new Vector3d(0,selection.variant().plateOffset(gauge),0).rotate(fit.rotation()));
            carrier=createBody(level,Content.MOUNT.get().defaultBlockState(),upperOrigin,fit.rotation());
            var mount=(MountEntity)level.getBlockEntity(carrier.getPlot().getCenterBlock());if(mount==null)throw new IllegalStateException("message.guidedrailways.placement.mount_failed");
            mount.gauge=gauge;mount.variant=selection.variant();mount.bogieId=bogie.getUniqueId();mount.setChanged();register(mount);tick(level);
            var u=ACTIVE.getOrDefault(level,Map.of()).get(bogie.getUniqueId());if(u!=null)seedPlacement(u,fit,route);
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[RailPlacement] action=place curve={} position={} frontArc={} rearArc={}",sample.path().edge().isTurn(),fit.origin(),fit.front(),fit.rear());
        }catch(RuntimeException e){var c=SubLevelContainer.getContainer(level);if(bogie!=null&&!bogie.isRemoved())c.removeSubLevel(bogie,SubLevelRemovalReason.REMOVED);if(carrier!=null&&!carrier.isRemoved())c.removeSubLevel(carrier,SubLevelRemovalReason.REMOVED);throw e;}
    }
    /** Mounting is a construction operation: rail is optional, the bearing position is exact. */
    private static void installBelow(ServerLevel level,UseOnContext context,ServerSubLevel carrier){
        if(context.getClickedFace()!=Direction.DOWN)throw new IllegalArgumentException("message.guidedrailways.placement.underside");
        var pos=context.getClickedPos().below();if(!level.getBlockState(pos).canBeReplaced())throw new IllegalArgumentException("message.guidedrailways.placement.no_space");
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();var rotation=new Quaterniond(pipe.readPose(carrier,new Pose3d()).orientation());
        var pivot=world(carrier,new Vector3d(pos.getX()+.5,pos.getY()+PLATE_BOTTOM,pos.getZ()+.5));
        var selection=PhysicalBogeySelection.selected((net.minecraft.server.level.ServerPlayer)context.getPlayer());
        var gauge=selection.gauge()==null?RailGauge.STANDARD:selection.gauge();
        var origin=new Vector3d(pivot).sub(new Vector3d(0,selection.variant().pivotHeight(BogieGeometry.MOUNT_HEIGHT_VERSION,gauge),0).rotate(rotation));
        checkPlacementSpace(level,origin,rotation,selection.variant(),gauge,List.of());var bogie=createBody(level,Content.BOGIE.get().defaultBlockState().setValue(BogieBlock.GAUGE,gauge.ordinal()).setValue(BogieBlock.STYLE,selection.variant().ordinal()),origin,rotation);
        applyBogeySelection(bogie,selection);
        var tag=bogie.getUserDataTag();if(tag==null)tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("GuidedDerailed",true);bogie.setUserDataTag(tag);
        try{
            level.setBlock(pos,Content.MOUNT.get().defaultBlockState(),3);
            if(!(level.getBlockEntity(pos) instanceof MountEntity mount))throw new IllegalStateException("message.guidedrailways.placement.mount_failed");
            mount.gauge=gauge;mount.variant=selection.variant();mount.bogieId=bogie.getUniqueId();mount.setChanged();register(mount);carrier.buildMassTracker();carrier.updateMergedMassData(1);pipe.onStatsChanged(carrier);tick(level);
            context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.guidedrailways.placement.installed"),true);
        }catch(RuntimeException e){SubLevelContainer.getContainer(level).removeSubLevel(bogie,SubLevelRemovalReason.REMOVED);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);throw e;}
    }
    private static void applyBogeySelection(ServerSubLevel body,PhysicalBogeySelection.Selection selection){
        if(body.getLevel().getBlockEntity(body.getPlot().getCenterBlock()) instanceof BogieEntity entity){entity.bogeyData=selection.data().copy();entity.setChanged();entity.sendData();}
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] body={} variant={} gauge={}",body.getUniqueId(),selection.variant(),BogieBlock.gauge(body.getLevel().getBlockState(body.getPlot().getCenterBlock())));
    }
    private static void seedPlacement(Unit u,RailPlacement.Bogie fit,TrackNetwork.PlacementRoute route){
        u.samples[0]=route.sample(fit.front());u.samples[1]=route.sample(fit.rear());
        u.derailGraceUntil=u.bogie.getLevel().getGameTime()+40;
    }
    private static void checkPlacementSpace(ServerLevel level,Vector3dc origin,Quaterniond rotation,BogeyVariant variant,RailGauge gauge,List<Unit> excluded){
        var offset=BogiePairContact.offset(gauge,variant);var half=BogiePairContact.half(gauge,variant);var centre=new Vector3d(offset).rotate(rotation).add(origin);
        for(int i=0;i<8;i++){
            var corner=new Vector3d((i&1)==0?-half.x():half.x(),(i&2)==0?-half.y():half.y(),(i&4)==0?-half.z():half.z()).rotate(rotation).add(centre);
            if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(corner.x,corner.y,corner.z)))throw new IllegalArgumentException("message.guidedrailways.placement.border");
        }
        var candidate=new BogiePairContact.Pose(centre,rotation,true,half);
        for(var u:ACTIVE.getOrDefault(level,Map.of()).values())if(!excluded.contains(u)&&!u.bogie.isRemoved()){
            var other=world(u.bogie,new Vector3d(u.localCenter).add(BogiePairContact.offset(u.mount.gauge,u.mount.variant)));
            var q=new Quaterniond(SubLevelPhysicsSystem.get(level).getPipeline().readPose(u.bogie,new Pose3d()).orientation());
            var contact=BogiePairContact.find(candidate,new BogiePairContact.Pose(other,q,true,BogiePairContact.half(u.mount.gauge,u.mount.variant)),null);
            if(contact!=null&&contact.gap()<.03)throw new IllegalArgumentException("message.guidedrailways.placement.too_close");
        }
    }
    private static boolean populateCollision(ServerSubLevel body,boolean refreshMass){
        var tag=body.getUserDataTag();if(tag==null)tag=new net.minecraft.nbt.CompoundTag();var centreState=body.getLevel().getBlockState(body.getPlot().getCenterBlock());var gauge=BogieBlock.gauge(centreState);var variant=BogieBlock.variant(centreState);if(tag.getInt("GuidedCollisionLayout")>=4&&RailGauge.read(tag.getString("GuidedCollisionGauge"))==gauge&&tag.getInt("GuidedCollisionVariant")==variant.ordinal())return true;
        var level=body.getLevel();var centre=body.getPlot().getCenterBlock();
        if(tag.getInt("GuidedCollisionLayout")>0&&tag.getInt("GuidedCollisionLayout")<4){
            for(var p:BlockPos.betweenClosed(centre.offset(-3,-3,-8),centre.offset(3,3,8)))if(level.getBlockState(p).is(Content.COLLISION.get())){
                var chunk=level.getChunkAt(p);var old=chunk.getBlockState(p);chunk.setBlockState(p,Blocks.AIR.defaultBlockState(),true);SubLevelAssemblyHelper.markAndNotifyBlock(level,p,chunk,old,Blocks.AIR.defaultBlockState(),3,512);
            }
        }
        for(var cell:BogieCollision.cells(gauge,variant)){
            if(cell.part()==BogieCollision.CENTRE_PART)continue;
            var state=level.getBlockState(centre.offset(cell.x(),cell.y(),cell.z()));
            if(!state.isAir()&&!state.is(Content.COLLISION.get()))return false;
        }
        for(var cell:BogieCollision.cells(gauge,variant)){
            if(cell.part()==BogieCollision.CENTRE_PART)continue;
            var pos=centre.offset(cell.x(),cell.y(),cell.z());var state=Content.COLLISION.get().defaultBlockState().setValue(CollisionPartBlock.PART,cell.part()).setValue(BogieBlock.GAUGE,gauge.ordinal()).setValue(BogieBlock.STYLE,variant.ordinal());
            var chunk=level.getChunkAt(pos);var old=chunk.getBlockState(pos);if(old.equals(state))continue;
            chunk.setBlockState(pos,state,true);SubLevelAssemblyHelper.markAndNotifyBlock(level,pos,chunk,old,state,3,512);
        }
        tag.putInt("GuidedCollisionLayout",4);tag.putInt("GuidedCollisionVariant",variant.ordinal());tag.putString("GuidedCollisionGauge",gauge.name());body.setUserDataTag(tag);
        if(refreshMass){
            var pipe=SubLevelPhysicsSystem.get(level).getPipeline();var before=pipe.readPose(body,new Pose3d());
            var oldReference=new Vector3d(body.logicalPose().rotationPoint());
            var velocity=pipe.getLinearVelocity(body,new Vector3d());var angular=pipe.getAngularVelocity(body,new Vector3d());
            body.buildMassTracker();body.updateMergedMassData(1);
            var com=new Vector3d(body.getMassTracker().getCenterOfMass());var shift=new Vector3d(com).sub(oldReference).rotate(before.orientation());
            body.logicalPose().rotationPoint().set(com);pipe.onStatsChanged(body);
            pipe.teleport(body,new Vector3d(before.position()).add(shift),before.orientation());pipe.resetVelocity(body);
            pipe.addLinearAndAngularVelocity(body,new Vector3d(angular).cross(shift).add(velocity),angular);
            body.updateLastPose();body.updateBoundingBox();
        }
        if(refreshMass){var mass=body.getMassTracker();var inertia=mass.getInertiaTensor();
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieMass] body={} mass={} inertiaDiagonal={},{},{} layout=distributed_v3",body.getUniqueId(),mass.getMass(),inertia.m00(),inertia.m11(),inertia.m22());
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieMassCentre] body={} localY={}",body.getUniqueId(),mass.getCenterOfMass().y()-centre.getY());}
        return true;
    }
    private static ServerSubLevel createBody(ServerLevel level,BlockState state,Vector3d origin,Quaterniond rotation){
        var container=SubLevelContainer.getContainer(level);var body=(ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        try{
            var plot=body.getPlot();plot.newEmptyChunk(plot.getCenterChunk());var pos=plot.getCenterBlock();var ref=center(body);var chunk=level.getChunkAt(pos);var old=chunk.getBlockState(pos);chunk.setBlockState(pos,state,true);SubLevelAssemblyHelper.markAndNotifyBlock(level,pos,chunk,old,state,3,512);
            if(state.is(Content.BOGIE.get())&&!populateCollision(body,false))throw new IllegalStateException("message.guidedrailways.placement.bogie_failed");
            body.buildMassTracker();body.updateMergedMassData(1);var com=body.getMassTracker().getCenterOfMass();body.logicalPose().rotationPoint().set(com);
            SubLevelPhysicsSystem.get(level).getPipeline().teleport(body,new Vector3d(com).sub(ref).rotate(rotation).add(origin),rotation);body.updateLastPose();body.updateBoundingBox();body.setName(net.minecraft.network.chat.Component.translatable(state.is(Content.MOUNT.get())?"block.guidedrailways.bogie_mount":"block.guidedrailways.bogie_frame").getString());return body;
        }catch(RuntimeException error){container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);throw error;}
    }
    static void unload(ServerLevel level){BogiePairContacts.unload(level);var units=ACTIVE.remove(level);if(units!=null){units.values().forEach(Unit::close);RELOCATING.values().removeIf(r->units.containsKey(r.bogie));}INPUTS.remove(level);MountBlock.clear(level);PerfCounters.unload(level);ConsistRoutes.unload(level);}
}
