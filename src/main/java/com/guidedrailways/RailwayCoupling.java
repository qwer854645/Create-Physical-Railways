package com.guidedrailways;
import com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity;
import com.railwayteam.railways.content.coupling.coupler.TrackCoupler;
import com.simibubi.create.Create;
import com.railwayteam.railways.registry.CREdgePointTypes;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.*;

/** The native markers identify the two physical carriers; only our real couplers are operated. */
public final class RailwayCoupling {
    public record Status(int mask,int mode){}
    private record Target(UUID graph,int a,int b,Vector3d point){}
    private static Target target(ServerLevel level,TrackCoupler point){
        if(point==null||point.edgeLocation==null)return null;
        for(var graph:Create.RAILWAYS.sided(level).trackNetworks.values())if(graph.getPoint(CREdgePointTypes.COUPLER,point.id)==point){
            var a=graph.locateNode(point.edgeLocation.getFirst());var b=graph.locateNode(point.edgeLocation.getSecond());if(a==null||b==null)return null;
            var edge=graph.getConnectionsFrom(a).get(b);if(edge==null||edge.getLength()<=0)return null;
            var p=edge.getPosition(graph,Math.clamp(point.getLocationOn(edge)/edge.getLength(),0,1));return new Target(graph.id,a.getNetId(),b.getNetId(),new Vector3d(p.x,p.y,p.z));
        }
        return null;
    }
    private static ServerSubLevel carrier(ServerLevel level,Target target){
        if(target==null)return null;ServerSubLevel selected=null;double best=1.25*1.25;
        for(var unit:VehiclePhysics.cargoUnits(level))if(!unit.detached&&!unit.bogie.isRemoved()&&!unit.carrier.isRemoved()&&unit.lastSeen+2>=level.getGameTime())for(var sample:unit.samples){
            if(sample==null||!sample.path().graph().equals(target.graph))continue;
            var path=sample.path();if(!(path.a()==target.a&&path.b()==target.b||path.a()==target.b&&path.b()==target.a))continue;
            double distance=sample.position().distanceSquared(target.point);
            if(distance<best){best=distance;selected=unit.carrier;}
        }
        return selected;
    }
    public static Status inspect(TrackCouplerBlockEntity tile,boolean execute){
        if(!(tile.getLevel() instanceof ServerLevel level))return new Status(-1,0);
        var a=carrier(level,target(level,tile.getCoupler()));var b=carrier(level,target(level,tile.getSecondaryCoupler()));
        int mask=(a==null?0:1)|(b==null?0:2);if(mask==0)return new Status(-1,0);
        var allowed=tile.getAllowedOperationMode();int mode=CouplerConnections.trackOperation(level,a,b,allowed.canCouple,allowed.canDecouple,execute);
        return new Status(mask,mode);
    }
}
