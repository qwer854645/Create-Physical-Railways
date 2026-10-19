package com.guidedrailways;
import com.railwayteam.railways.mixin_interfaces.ISwitchDisabledEdge;
import com.railwayteam.railways.util.MixinVariables;
import com.simibubi.create.content.trains.graph.TrackEdge;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Optional release-jar adapter; never constructs native trains or carriages. */
final class SteamRailways implements RailwaysCompat.Bridge {
    private final Map<ServerLevel,SwitchReservations> reservations=new WeakHashMap<>();
    static void register(){RailwaysCompat.bridge=new SteamRailways();}
    @Override public boolean geometry(TrackEdge from,TrackEdge to){
        boolean previous=MixinVariables.temporarilySkipSwitches;
        try{MixinVariables.temporarilySkipSwitches=true;return from.canTravelTo(to);}
        finally{MixinVariables.temporarilySkipSwitches=previous;}
    }
    private static ISwitchDisabledEdge flags(TrackEdge edge){return (ISwitchDisabledEdge)edge.getEdgeData();}
    @Override public boolean navigable(TrackEdge to){var f=flags(to);return f.isEnabled()||f.isAutomatic();}
    @Override public boolean pass(ServerLevel level,TrackNetwork.Path path,int node,int from,int to,UUID train,boolean committed){
        var target=path.network().getConnectionsFrom(path.network().getNode(node)).get(path.network().getNode(to));
        if(target==null)return false;var f=flags(target);
        var claims=reservations.computeIfAbsent(level,l->new SwitchReservations());var key=new SwitchReservations.Key(path.graph(),node);long now=level.getGameTime();
        if(claims.owns(key,train,from,to,now)&&committed)return true;
        if(!f.isEnabled()&&!f.isAutomatic())return false;
        if(!claims.claim(key,train,from,to,now))return false;
        if(!f.isEnabled()){f.setAutomaticallySelected();return false;}
        return true;
    }
    @Override public void hold(ServerLevel level,UUID graph,int node,int from,int to,UUID train){reservations.computeIfAbsent(level,l->new SwitchReservations()).claim(new SwitchReservations.Key(graph,node),train,from,to,level.getGameTime());}
    @Override public void unload(ServerLevel level){reservations.remove(level);}
}
