package com.guidedrailways;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import java.util.*;
/** Sable exposes pair contact suppression through joints. All six axes stay FREE. */
final class BogieContacts {
    private record Live(ServerSubLevel a,ServerSubLevel b,GenericConstraintHandle handle){
        void close(){if(handle.isValid())handle.remove();}
    }
    private static final Map<ServerLevel,Map<BogieContactPolicy.Pair,Live>> STATES=new WeakHashMap<>();
    static void refresh(ServerLevel level,Collection<VehiclePhysics.Unit> units){
        var bodies=new ArrayList<BogieContactPolicy.Body>();var byId=new HashMap<UUID,VehiclePhysics.Unit>();
        for(var u:units){
            if(u.bogie.isRemoved()||u.carrier.isRemoved()||u.mount.isRemoved())continue;
            var p=VehiclePhysics.world(u.bogie,u.localCenter);var id=u.bogie.getUniqueId();byId.put(id,u);
            bodies.add(new BogieContactPolicy.Body(id,u.carrier.getUniqueId(),p.x,p.y,p.z,u.detached));
        }
        var links=new ArrayList<BogieContactPolicy.Link>();
        for(var l:CouplerConnections.bodyLinks(level))links.add(new BogieContactPolicy.Link(l.a().getUniqueId(),l.b().getUniqueId()));
        var wanted=BogieContactPolicy.exclusions(bodies,links);var live=STATES.computeIfAbsent(level,k->new HashMap<>());
        int before=live.size();
        live.entrySet().removeIf(e->{var v=e.getValue();var a=byId.get(e.getKey().a());var b=byId.get(e.getKey().b());
            if(!wanted.contains(e.getKey())||!v.handle.isValid()||a==null||b==null||v.a!=a.bogie||v.b!=b.bogie){v.close();return true;}return false;});
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        for(var pair:wanted)if(!live.containsKey(pair)){
            var a=byId.get(pair.a());var b=byId.get(pair.b());
            var joint=pipe.addConstraint(a.bogie,b.bogie,new GenericConstraintConfiguration(a.localCenter,b.localCenter,new Quaterniond(),new Quaterniond(),EnumSet.noneOf(ConstraintJointAxis.class)));
            joint.setContactsEnabled(false);live.put(pair,new Live(a.bogie,b.bogie,joint));
        }
        if(before!=live.size())org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieContacts] excludedPairs={} mode=same_consist_free_axes",live.size());
    }
    static void unload(ServerLevel level){var live=STATES.remove(level);if(live!=null)live.values().forEach(Live::close);}
}
