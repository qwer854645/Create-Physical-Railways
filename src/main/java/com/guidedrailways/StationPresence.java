package com.guidedrailways;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import java.util.*;

/** Adds physical arrivals to station flags/comparators without changing native Train ownership. */
public final class StationPresence {
    private record Key(UUID console,UUID station){}
    private static final class State {final Map<Key,StationStopState> stops=new HashMap<>();Set<BlockPos> present=Set.of();}
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    public static boolean present(Level level,BlockPos pos){return level instanceof ServerLevel server&&STATES.containsKey(server)&&STATES.get(server).present.contains(pos);}
    static void tick(ServerLevel level,Collection<VehiclePhysics.Unit> units){
        var state=STATES.computeIfAbsent(level,k->new State());var next=new HashSet<BlockPos>();var seen=new HashSet<Key>();
        var groups=new HashMap<DriverConsoleEntity,List<VehiclePhysics.Unit>>();
        for(var u:units)if(u.driver!=null&&!u.carrier.isRemoved()&&!u.bogie.isRemoved()&&!u.mount.isRemoved()&&u.lastSeen+2>=level.getGameTime())groups.computeIfAbsent(u.driver,k->new ArrayList<>()).add(u);
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();var links=CouplerConnections.bodyLinks(level);
        for(var entry:groups.entrySet()){
            var owner=entry.getKey();var group=entry.getValue();if(group.stream().anyMatch(u->u.detached))continue;
            int direction=Math.abs(owner.actual)>.12?(owner.actual<0?-1:1):owner.direction==0?1:owner.direction;
            var basis=AutomaticTrains.basis(owner,group,links,direction);if(basis==null)continue;
            var head=basis.head();int end=basis.end();var heading=new Vector3d(0,0,1).rotate(pipe.readPose(head.bogie,new Pose3d()).orientation());
            var sample=TrackNetwork.nearest(level,VehiclePhysics.world(head.bogie,head.axle(end)),heading,head.samples[end]==null?null:head.samples[end].path(),1,head.mount.gauge);if(sample==null)continue;
            var path=sample.path();boolean increasing=PathGeometry.tangent(path::at,sample.t()).dot(heading)*basis.signs().get(head.bogie.getUniqueId())>=0;
            int from=increasing?path.a():path.b(),to=increasing?path.b():path.a();var fromNode=path.network().getNode(from);var toNode=path.network().getNode(to);if(fromNode==null||toNode==null)continue;
            var edge=TrackNetwork.connections(path.network(),fromNode).get(toNode);if(edge==null)continue;
            double speed=0;for(var u:group){speed=Math.max(speed,pipe.getLinearVelocity(u.bogie,new Vector3d()).length());speed=Math.max(speed,pipe.getLinearVelocity(u.carrier,new Vector3d()).length());}
            for(var station:path.network().getPoints(EdgePointType.STATION)){
                if(station.edgeLocation==null||!station.edgeLocation.getFirst().equals(fromNode.getLocation())||!station.edgeLocation.getSecond().equals(toNode.getLocation())||!station.canApproachFrom(toNode)||!level.dimension().equals(station.getBlockEntityDimension()))continue;
                var pos=station.getBlockEntityPos();if(pos==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof StationBlockEntity))continue;
                double distance=station.getLocationOn(edge)-(increasing?sample.t():1-sample.t())*edge.getLength();
                if(Math.abs(distance)>1)continue;var key=new Key(owner.consoleId,station.id);seen.add(key);
                if(state.stops.computeIfAbsent(key,k->new StationStopState()).tick(distance,speed,true))next.add(pos.immutable());
            }
        }
        state.stops.keySet().removeIf(k->!seen.contains(k));var changed=new HashSet<>(state.present);changed.addAll(next);changed.removeIf(p->state.present.contains(p)==next.contains(p));state.present=Set.copyOf(next);
        for(var pos:changed)if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof StationBlockEntity station){
            station.sendData();level.updateNeighbourForOutputSignal(pos,level.getBlockState(pos).getBlock());
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[PhysicalStation] pos={} arrived={} comparator={}",pos,next.contains(pos),next.contains(pos)?15:0);
        }
    }
    static void unload(ServerLevel level){STATES.remove(level);}
}
