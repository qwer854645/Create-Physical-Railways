package com.guidedrailways;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import java.util.*;
/** Server-only registry; one authority on a carrier, no remote control packets or extra bodies. */
public final class DriverControls {
    private static final Map<ServerLevel,Map<BlockPos,DriverConsoleEntity>> CONSOLES=new WeakHashMap<>();
    private DriverControls(){}
    static void register(ServerLevel level,DriverConsoleEntity e){CONSOLES.computeIfAbsent(level,l->new HashMap<>()).put(e.getBlockPos().immutable(),e);}
    static ServerSubLevel body(DriverConsoleEntity e){return e.getLevel()!=null&&SableCompanion.INSTANCE.getContaining(e.getLevel(),e.getBlockPos()) instanceof ServerSubLevel b&&!b.isRemoved()?b:null;}
    static void claim(ServerLevel level,DriverConsoleEntity e){
        register(level,e);var body=body(e);if(body==null)return;
        for(var other:CONSOLES.get(level).values())if(other!=e&&body(other)==body){other.park();}
    }
    static void emergency(ServerLevel level,DriverConsoleEntity source){
        source.emergency=true;source.notch=-4;source.updateVisual();var body=body(source);if(body==null)return;
        for(var e:CONSOLES.getOrDefault(level,Map.of()).values())if(body(e)==body){e.emergency=true;e.notch=-4;e.updateVisual();}
    }
    static void resetEmergency(ServerLevel level,DriverConsoleEntity source){
        var body=body(source);source.emergency=false;source.notch=-4;source.updateVisual();if(body==null)return;
        for(var e:CONSOLES.getOrDefault(level,Map.of()).values())if(body(e)==body){e.emergency=false;e.notch=-4;e.updateVisual();}
    }
    static void removed(ServerLevel level,DriverConsoleEntity e){
        var map=CONSOLES.get(level);if(map!=null)map.remove(e.getBlockPos(),e);
        e.active=false;e.emergency=true;e.notch=-4;VehiclePhysics.consoleRemoved(e);
    }
    static void refresh(ServerLevel level,Collection<VehiclePhysics.Unit> units){
        var map=CONSOLES.getOrDefault(level,Map.of());
        if(!map.isEmpty())map.values().removeIf(e->e.isRemoved()||!level.hasChunkAt(e.getBlockPos())||level.getBlockEntity(e.getBlockPos())!=e);
        var chosen=new HashMap<ServerSubLevel,DriverConsoleEntity>();
        for(var e:map.values()){
            e.actual=0;e.available=0;e.supports=0;e.powered=0;e.guided=0;e.mountBrakes=0;var body=body(e);if(body==null)continue;
            var old=chosen.get(body);
            if(old==null||e.active&&!old.active||e.active==old.active&&e.getBlockPos().asLong()<old.getBlockPos().asLong())chosen.put(body,e);
        }
        for(var u:units){
            var next=chosen.get(u.carrier);
            if(u.driver!=null&&next==null){u.mount.braking=true;u.mount.setChanged();u.mount.sendData();}
            u.driver=next;
        }
        for(var e:map.values()){
            var body=body(e);if(body==null)continue;
            var pipe=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline();
            var facing=e.getBlockState().getValue(DriverConsoleBlock.FACING);
            var heading=new Vector3d(facing.getStepX(),0,facing.getStepZ()).rotate(pipe.readPose(body,new dev.ryanhcode.sable.companion.math.Pose3d()).orientation());
            e.actual=pipe.getLinearVelocity(body,new Vector3d()).dot(heading);
            for(var u:units)if(u.carrier==body){e.supports++;if(!u.detached)e.guided++;if(u.mount.braking)e.mountBrakes++;if(Math.abs(u.mount.getSpeed())>.001){e.powered++;double cap=Math.abs(u.mount.getSpeed())*TrainConfig.SPEED_PER_RPM.get();e.available=e.powered==1?cap:Math.min(e.available,cap);}}
            if(e.active&&level.getGameTime()%20==0)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[DriverState] body={} actual={} notch={} available={} direction={} emergency={} powered={} guided={}/{}",body.getUniqueId(),e.actual,e.notch,e.available,e.direction,e.emergency,e.powered,e.guided,e.supports);
        }
    }
    static Vector3d position(DriverConsoleEntity e){
        var p=new Vector3d(e.getBlockPos().getX()+.5,e.getBlockPos().getY()+.5,e.getBlockPos().getZ()+.5);var body=body(e);return body==null?p:VehiclePhysics.world(body,p);
    }
    static boolean canChangeDirection(DriverConsoleEntity e){
        var body=body(e);return body!=null&&DriverModel.canChangeDirection(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(body.getLevel()).getPipeline().getLinearVelocity(body,new Vector3d()).length(),e.notch);
    }
    static boolean accessible(DriverConsoleEntity e,Player player){
        if(e.isRemoved()||player.level()!=e.getLevel()||e.getLevel().getBlockEntity(e.getBlockPos())!=e)return false;
        var p=position(e);return player.distanceToSqr(p.x,p.y,p.z)<=64;
    }
    static void unload(ServerLevel level){CONSOLES.remove(level);}
}
