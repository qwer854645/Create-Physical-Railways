package com.guidedrailways;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import java.util.*;
/** One oldest console controls an entire live coupled consist, never one console per carriage. */
public final class DriverControls {
    private static final Map<ServerLevel,Map<BlockPos,DriverConsoleEntity>> CONSOLES=new WeakHashMap<>();
    private static final Map<ServerLevel,List<VehiclePhysics.Unit>> DRIVEN=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,DriverConsoleEntity>> VIEW=new WeakHashMap<>();
    private DriverControls(){}
    static void register(ServerLevel level,DriverConsoleEntity e){if(!e.identitySaved){e.identitySaved=true;e.setChanged();}CONSOLES.computeIfAbsent(level,l->new HashMap<>()).put(e.getBlockPos().immutable(),e);}
    static ServerSubLevel body(DriverConsoleEntity e){return e.getLevel()!=null&&SableCompanion.INSTANCE.getContaining(e.getLevel(),e.getBlockPos()) instanceof ServerSubLevel b&&!b.isRemoved()?b:null;}
    private static Map<UUID,DriverConsoleEntity> election(ServerLevel level){
        var records=new ArrayList<ConsoleElection.Console>();var byId=new HashMap<UUID,DriverConsoleEntity>();
        for(var e:CONSOLES.getOrDefault(level,Map.of()).values())if(!e.isRemoved()&&level.hasChunkAt(e.getBlockPos())&&level.getBlockEntity(e.getBlockPos())==e){
            var b=body(e);if(b!=null){records.add(new ConsoleElection.Console(e.consoleId,b.getUniqueId(),e.placementOrder));byId.put(e.consoleId,e);}
        }
        var links=new ArrayList<ConsoleElection.Link>();for(var link:CouplerConnections.bodyLinks(level))links.add(new ConsoleElection.Link(link.a().getUniqueId(),link.b().getUniqueId()));
        var result=new HashMap<UUID,DriverConsoleEntity>();ConsoleElection.elect(records,links).forEach((carrier,id)->result.put(carrier,byId.get(id)));return result;
    }
    static boolean isPrimary(DriverConsoleEntity e){
        if(!(e.getLevel() instanceof ServerLevel level)||e.isRemoved())return false;
        register(level,e);var b=body(e);return b==null||election(level).get(b.getUniqueId())==e;
    }
    static boolean primaryView(DriverConsoleEntity e){var b=body(e);return e.getLevel() instanceof ServerLevel level&&(b==null||VIEW.getOrDefault(level,Map.of()).get(b.getUniqueId())==e);}
    static void secondaryHint(Player player){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.guidedrailways.driver.secondary"),true);}
    static void claim(ServerLevel level,DriverConsoleEntity e,Player player){
        register(level,e);if(!isPrimary(e))return;
        for(var other:CONSOLES.get(level).values())if(other!=e&&player.getUUID().equals(other.operator))other.leave();
    }
    static void emergency(ServerLevel level,DriverConsoleEntity source){source.emergency=true;source.notch=-4;source.updateVisual();}
    static void resetEmergency(ServerLevel level,DriverConsoleEntity source){source.emergency=false;source.notch=-4;source.updateVisual();}
    static void removed(ServerLevel level,DriverConsoleEntity e){
        var map=CONSOLES.get(level);if(map!=null)map.remove(e.getBlockPos(),e);
        e.active=false;e.enabled=false;e.steer=0;e.emergency=true;e.notch=-4;DriverNetwork.sync(e,false);e.operator=null;VehiclePhysics.consoleRemoved(e);
    }
    static void refresh(ServerLevel level,Collection<VehiclePhysics.Unit> units){
        var map=CONSOLES.getOrDefault(level,Map.of());
        if(!map.isEmpty())map.values().removeIf(e->{boolean gone=e.isRemoved()||!level.hasChunkAt(e.getBlockPos())||level.getBlockEntity(e.getBlockPos())!=e;if(gone&&e.active)e.park();return gone;});
        for(var e:map.values())if(e.active){var operator=e.operator==null?null:level.getServer().getPlayerList().getPlayer(e.operator);if(operator==null||!operator.isAlive()||!accessible(e,operator))e.park();}
        DRIVEN.put(level,List.copyOf(units));
        var chosen=election(level);VIEW.put(level,chosen);
        for(var e:map.values()){
            e.actual=0;e.available=0;e.supports=0;e.powered=0;e.guided=0;e.mountBrakes=0;var b=body(e);
            if(b!=null&&chosen.get(b.getUniqueId())!=e&&(e.active||e.enabled||e.schedule.running||e.schedule.manualOverride))e.park();
        }
        for(var u:units){
            var next=chosen.get(u.carrier.getUniqueId());
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
        }
        AutomaticTrains.tick(level,units,map.values());
        StationPresence.tick(level,units);
        for(var e:map.values()){
            var body=body(e);if(body==null)continue;
            if(e.active&&level.getGameTime()%5==0)DriverNetwork.sync(e,true);
            if((e.active||e.enabled)&&level.getGameTime()%20==0)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[DriverState] body={} actual={} notch={} available={} direction={} operatorActive={} driveEnabled={} emergency={} powered={} guided={}/{}",body.getUniqueId(),e.actual,e.notch,e.available,e.direction,e.active,e.enabled,e.emergency,e.powered,e.guided,e.supports);
        }
    }
    static void remote(net.minecraft.server.level.ServerPlayer player,BlockPos pos,int command){
        if(!DriverInputPolicy.remoteCommand(command))return;
        var e=CONSOLES.getOrDefault(player.serverLevel(),Map.of()).get(pos);
        if(e==null||!e.active||!player.getUUID().equals(e.operator)||command!=19&&!isPrimary(e))return;
        if(!accessible(e,player)){e.park();return;}
        e.command(player,command);
    }
    static Vector3d position(DriverConsoleEntity e){
        var p=new Vector3d(e.getBlockPos().getX()+.5,e.getBlockPos().getY()+.5,e.getBlockPos().getZ()+.5);var body=body(e);return body==null?p:VehiclePhysics.world(body,p);
    }
    static boolean canChangeDirection(DriverConsoleEntity e){
        var body=body(e);if(body==null)return false;var pipe=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(body.getLevel()).getPipeline();
        double speed=pipe.getLinearVelocity(body,new Vector3d()).length();
        for(var u:DRIVEN.getOrDefault(body.getLevel(),List.of()))if(u.driver==e&&!u.bogie.isRemoved())speed=Math.max(speed,pipe.getLinearVelocity(u.bogie,new Vector3d()).length());
        return DriverModel.canChangeDirection(speed,e.notch);
    }
    static boolean stopped(DriverConsoleEntity e){
        var body=body(e);if(body==null)return true;
        var pipe=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(body.getLevel()).getPipeline();
        var members=new HashMap<UUID,ServerSubLevel>();members.put(body.getUniqueId(),body);
        var links=CouplerConnections.bodyLinks(body.getLevel());boolean changed;
        do{changed=false;for(var link:links)if(members.containsKey(link.a().getUniqueId())||members.containsKey(link.b().getUniqueId())){
            changed|=members.putIfAbsent(link.a().getUniqueId(),link.a())==null;
            changed|=members.putIfAbsent(link.b().getUniqueId(),link.b())==null;
        }}while(changed);
        for(var u:DRIVEN.getOrDefault(body.getLevel(),List.of()))if(members.containsKey(u.carrier.getUniqueId())&&!u.bogie.isRemoved())members.put(u.bogie.getUniqueId(),u.bogie);
        for(var member:members.values()){
            double speed=pipe.getLinearVelocity(member,new Vector3d()).length();
            double angular=pipe.getAngularVelocity(member,new Vector3d()).length();
            if(!Double.isFinite(speed)||!Double.isFinite(angular)||speed>.15||angular>.15)return false;
        }
        return true;
    }
    static boolean accessible(DriverConsoleEntity e,Player player){
        if(e.isRemoved()||player.level()!=e.getLevel()||e.getLevel().getBlockEntity(e.getBlockPos())!=e)return false;
        var p=position(e);return player.distanceToSqr(p.x,p.y,p.z)<=64;
    }
    static void unload(ServerLevel level){CONSOLES.remove(level);VIEW.remove(level);DRIVEN.remove(level);StationPresence.unload(level);}
}
