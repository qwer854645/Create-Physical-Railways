package com.guidedrailways;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import java.util.*;
import java.util.regex.Pattern;

/** Adapter to the installed Create graph; no synthetic Create Train or carriage entities. */
final class AutomaticNavigation implements ScheduleRoute.Network {
    final ServerLevel level;final UUID graph;final Map<ScheduleRoute.Edge,TrackNetwork.Path> paths=new HashMap<>();
    AutomaticNavigation(ServerLevel level,UUID graph){
        this.level=level;this.graph=graph;
        for(var p:TrackNetwork.paths(level))if(p.graph().equals(graph)&&p.supports(RailGauge.STANDARD)){
            paths.put(new ScheduleRoute.Edge(p.a(),p.b()),p);paths.put(new ScheduleRoute.Edge(p.b(),p.a()),p);
        }
    }
    @Override public double length(ScheduleRoute.Edge edge){var p=paths.get(edge);return p==null?Double.POSITIVE_INFINITY:p.edge().getLength();}
    @Override public boolean clear(ScheduleRoute.Edge edge,double from,double to){
        var p=paths.get(edge);if(p==null)return false;
        for(double buffer:RailwaysCompat.buffers(p)){double d=p.a()==edge.from()?buffer:length(edge)-buffer;if(d>=Math.min(from,to)-1e-6&&d<=Math.max(from,to)+1e-6)return false;}
        return true;
    }
    @Override public List<ScheduleRoute.Edge> exits(ScheduleRoute.Edge edge){
        var p=paths.get(edge);if(p==null)return List.of();var node=p.network().getNode(edge.to());var from=p.network().getNode(edge.from());if(node==null||from==null)return List.of();
        var incoming=p.network().getConnectionsFrom(from).get(node);if(incoming==null)return List.of();var out=new ArrayList<ScheduleRoute.Edge>();
        for(var entry:p.network().getConnectionsFrom(node).entrySet()){
            var next=new ScheduleRoute.Edge(edge.to(),entry.getKey().getNetId());
            if(next.to()!=edge.from()&&!entry.getValue().isInterDimensional()&&RailwaysCompat.geometry(incoming,entry.getValue())&&RailwaysCompat.navigable(entry.getValue())&&paths.containsKey(next))out.add(next);
        }
        return out;
    }
    List<ScheduleRoute.Goal> goals(DestinationInstruction instruction,UUID preferred){
        Pattern pattern=Pattern.compile(instruction.getFilterForRegex());
        return goals(station->pattern.matcher(station.name).matches(),preferred);
    }
    List<ScheduleRoute.Goal> goals(java.util.function.Predicate<GlobalStation> matches,UUID preferred){
        var out=new ArrayList<ScheduleRoute.Goal>();if(paths.isEmpty())return out;
        var network=paths.values().iterator().next().network();
        for(GlobalStation station:network.getPoints(EdgePointType.STATION)){
            if(!RailwaysCompat.stationEnabled(station))continue;
            if(!matches.test(station)||preferred!=null&&!preferred.equals(station.id)||station.edgeLocation==null)continue;
            var from=network.locateNode(station.edgeLocation.getFirst());var to=network.locateNode(station.edgeLocation.getSecond());
            if(from==null||to==null||!station.canApproachFrom(to))continue;
            var edge=new ScheduleRoute.Edge(from.getNetId(),to.getNetId());var real=network.getConnectionsFrom(from).get(to);
            if(!paths.containsKey(edge)||real==null)continue;
            out.add(new ScheduleRoute.Goal(station.id,station.name,edge,station.getLocationOn(real)));
        }
        out.sort(Comparator.comparing(g->g.id().toString()));return out;
    }
    double offset(TrackNetwork.Sample sample,ScheduleRoute.Edge edge){return (sample.path().a()==edge.from()?sample.t():1-sample.t())*length(edge);}
    ScheduleRoute.Edge direction(TrackNetwork.Sample sample,Vector3d travel){
        var p=sample.path();boolean increasing=PathGeometry.tangent(p::at,sample.t()).dot(travel)>=0;
        return new ScheduleRoute.Edge(increasing?p.a():p.b(),increasing?p.b():p.a());
    }
    boolean live(ScheduleRoute.Route route){
        for(var edge:route.edges()){var p=paths.get(edge);if(p==null||!TrackNetwork.live(level,p))return false;}
        var p=paths.get(route.goal().edge());var station=p.network().getPoint(EdgePointType.STATION,route.goal().id());
        var from=p.network().getNode(route.goal().edge().from());var to=p.network().getNode(route.goal().edge().to());
        var real=from==null||to==null?null:p.network().getConnectionsFrom(from).get(to);
        return station!=null&&RailwaysCompat.stationEnabled(station)&&real!=null&&station.edgeLocation!=null&&station.edgeLocation.getFirst().equals(from.getLocation())&&station.edgeLocation.getSecond().equals(to.getLocation())
            &&station.canApproachFrom(to)&&station.name.equals(route.goal().name())&&Math.abs(station.getLocationOn(real)-route.goal().offset())<1e-6;
    }
    boolean loaded(ScheduleRoute.Route route,int leg,double offset){
        for(int i=leg;i<route.edges().size();i++){
            var edge=route.edges().get(i);var p=paths.get(edge);double lo=i==leg?offset:0,hi=i==route.edges().size()-1?route.goal().offset():length(edge);
            for(double d=lo;d<hi+4;d+=4){double f=Math.clamp(Math.min(d,hi)/length(edge),0,1);var point=p.at(p.a()==edge.from()?f:1-f);
                if(!level.hasChunkAt(BlockPos.containing(point.x,point.y,point.z)))return false;
            }
        }
        return true;
    }
}
