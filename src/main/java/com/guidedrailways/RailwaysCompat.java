package com.guidedrailways;
import com.simibubi.create.content.trains.graph.TrackEdge;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Core facade stays loadable without Steam 'n' Rails. */
final class RailwaysCompat {
    interface Bridge {
        boolean geometry(TrackEdge from,TrackEdge to);
        boolean navigable(TrackEdge to);
        boolean pass(ServerLevel level,TrackNetwork.Path path,int node,int from,int to,UUID train,boolean committed);
        void hold(ServerLevel level,UUID graph,int node,int from,int to,UUID train);
        void unload(ServerLevel level);
        double[] buffers(TrackNetwork.Path path);
        boolean stationEnabled(com.simibubi.create.content.trains.station.GlobalStation station);
    }
    static Bridge bridge;
    static boolean geometry(TrackEdge from,TrackEdge to){return bridge==null?from.canTravelTo(to):bridge.geometry(from,to);}
    static boolean navigable(TrackEdge to){return bridge==null||bridge.navigable(to);}
    static boolean pass(ServerLevel l,TrackNetwork.Path p,int node,int from,int to,UUID train,boolean committed){return bridge==null||bridge.pass(l,p,node,from,to,train,committed);}
    static void hold(ServerLevel l,RouteMemory.Active passage,UUID train){if(bridge!=null)bridge.hold(l,passage.node().graph(),passage.node().node(),passage.from(),passage.to(),train);}
    static void unload(ServerLevel l){if(bridge!=null)bridge.unload(l);}
    static double[] buffers(TrackNetwork.Path p){return bridge==null?new double[0]:bridge.buffers(p);}
    static boolean stationEnabled(com.simibubi.create.content.trains.station.GlobalStation s){return bridge==null||bridge.stationEnabled(s);}
}
