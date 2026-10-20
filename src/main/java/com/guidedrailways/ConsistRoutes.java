package com.guidedrailways;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.*;
/** Connected bodies share passage history. Physical leading ends are processed before their followers. */
final class ConsistRoutes {
    private static final Map<ServerLevel,Map<UUID,Context>> STATES=new WeakHashMap<>();
    private record Cached(long tick,List<Plan> plans){}
    private static final Map<ServerLevel,Cached> CACHE=new WeakHashMap<>();
    private static final class Context {
        final RouteMemory memory;final ServerLevel level;final UUID identity;final Map<RouteMemory.Axle,Vector3d> anchors=new HashMap<>();TrackNetwork.Sample head;int intent;ScheduleController automatic;double switchRoom=Double.POSITIVE_INFINITY;
        Context(RouteMemory memory,ServerLevel level,UUID carrier){this.memory=memory;this.level=level;identity=memory.reservationIdentity(carrier);}
    }
    static final class Cursor implements TrackNetwork.RoutePolicy {
        final Context context;final RouteMemory.Axle axle;final boolean leading;final RailGauge gauge;
        boolean switchWaiting;
        Cursor(Context c,RouteMemory.Axle a,boolean leading,RailGauge gauge){context=c;axle=a;this.leading=leading;this.gauge=gauge;}
        @Override public RailGauge gauge(){return gauge;}
        @Override public TrackNetwork.Sample leader(){return leading?null:context.head;}
        void followed(TrackNetwork.Sample sample){if(leading)context.head=sample;}
        double limit(double room){if(leading)context.switchRoom=room;return Math.min(room,context.switchRoom);}
        @Override public BranchSelect.Steer steer(){return context.intent<0?BranchSelect.Steer.LEFT:context.intent>0?BranchSelect.Steer.RIGHT:BranchSelect.Steer.NONE;}
        @Override public boolean blocked(){return switchWaiting||leading&&context.automatic!=null&&context.automatic.running&&(context.automatic.plan==null||context.automatic.routeRetry);}
        @Override public boolean mayPass(TrackNetwork.Path path,int node,int from,int to){
            boolean recorded=context.memory.active().stream().anyMatch(p->p.node().equals(new RouteMemory.Junction(path.graph(),node))&&(p.from()==from&&p.to()==to||p.from()==to&&p.to()==from));
            boolean allowed=RailwaysCompat.pass(context.level,path,node,from,to,context.identity,recorded);
            switchWaiting=!allowed;return allowed;
        }
        @Override public void observe(Vector3dc position){var p=context.anchors.get(axle);if(p!=null&&p.distanceSquared(position)>1.44){context.memory.clearRepeat(axle);context.anchors.remove(axle);}}
        @Override public int choose(TrackNetwork.Path path,int node,int from,int[] exits,int proposed,Vector3d position){
            boolean planned=leading&&context.automatic!=null&&context.automatic.running&&context.automatic.plan!=null;
            if(leading&&context.automatic!=null&&context.automatic.running&&(context.automatic.plan==null||context.automatic.routeRetry))return -1;
            if(planned){
                proposed=context.automatic.plan.choose(path,node,from);
                final int expected=proposed;
                if(expected<0||java.util.Arrays.stream(exits).noneMatch(exit->exit==expected)){
                    context.automatic.routeRetry=true;
                    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("[AutomaticJunctionRetry] console={} bogie={} node={} from={} planned={} exits={} target={}",context.automatic.owner.getBlockPos(),axle.bogie(),node,from,expected,java.util.Arrays.toString(exits),context.automatic.station);
                    return -1;
                }
            }
            return context.memory.preview(axle,new RouteMemory.Junction(path.graph(),node),from,exits,proposed,planned);
        }
        @Override public boolean commit(TrackNetwork.Path path,int node,int from,int[] exits,int selected,Vector3d position){
            boolean planned=leading&&context.automatic!=null&&context.automatic.running&&context.automatic.plan!=null;
            int committed=context.memory.commit(axle,new RouteMemory.Junction(path.graph(),node),from,exits,selected,planned);
            if(committed!=selected)return false;
            context.anchors.put(axle,new Vector3d(position));
            if(planned){
                int expected=context.automatic.plan.choose(path,node,from);
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[AutomaticJunction] console={} bogie={} axle={} node={} from={} planned={} selected={} target={}",context.automatic.owner.getBlockPos(),axle.bogie(),axle.end(),node,from,expected,selected,context.automatic.station);
                if(expected!=selected)context.automatic.routeRetry=true;
            }
            return true;
        }
    }
    record Plan(VehiclePhysics.Unit unit,Cursor front,Cursor rear){Cursor axle(int i){return i==0?front:rear;}}
    static List<Plan> prepare(ServerLevel level,Collection<VehiclePhysics.Unit> input){
        var cached=CACHE.get(level);if(cached!=null&&cached.tick==level.getGameTime())return cached.plans;
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();var units=new ArrayList<VehiclePhysics.Unit>();
        for(var u:input)if(!u.carrier.isRemoved()&&!u.bogie.isRemoved()&&!u.mount.isRemoved()&&u.lastSeen+2>=level.getGameTime())units.add(u);
        var graph=new HashMap<UUID,Set<UUID>>();var sockets=new HashMap<UUID,Vector3d>();
        for(var u:units)graph.computeIfAbsent(u.carrier.getUniqueId(),k->new HashSet<>());
        for(var link:TrainConsists.get(level).links()){
            UUID a=link.a().getUniqueId(),b=link.b().getUniqueId();graph.computeIfAbsent(a,k->new HashSet<>()).add(b);graph.computeIfAbsent(b,k->new HashSet<>()).add(a);
            sockets.put(a,VehiclePhysics.world(link.a(),link.anchorA()));sockets.put(b,VehiclePhysics.world(link.b(),link.anchorB()));
        }
        var old=STATES.getOrDefault(level,Map.of());var next=new HashMap<UUID,Context>();var output=new ArrayList<Plan>();
        for(var component:TrainConsists.get(level).groups()){
            var members=component.members();var group=new ArrayList<>(component.units());if(group.isEmpty())continue;
            group.sort(Comparator.comparing(u->u.carrier.getUniqueId()));
            UUID seedId=group.getFirst().carrier.getUniqueId();var queue=new ArrayDeque<UUID>();
            var axleIds=new HashSet<RouteMemory.Axle>();for(var u:group)if(!u.detached)for(int i=0;i<2;i++)axleIds.add(new RouteMemory.Axle(u.bogie.getUniqueId(),i));
            UUID head=seedId;double best=-Double.MAX_VALUE;
            for(var u:group){
                UUID id=u.carrier.getUniqueId();if(graph.getOrDefault(id,Set.of()).size()>1||u.detached)continue;
                var centre=VehiclePhysics.world(u.bogie,u.localCenter);var heading=new Vector3d(0,0,1).rotate(pipe.readPose(u.bogie,new Pose3d()).orientation());
                double speed=pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(heading);
                boolean reverse=BranchSelect.reverseTravel(speed,VehicleTraction.requestedSpeed(u),u.reverseTravel);
                var travel=new Vector3d(heading).mul(reverse?-1:1);var socket=sockets.get(id);
                double rank=socket==null?0:new Vector3d(centre).sub(socket).normalize().dot(travel);
                if(Double.isFinite(rank)&&rank>best){best=rank;head=id;}
            }
            VehiclePhysics.Unit automaticHead=null;int automaticEnd=0;
            for(var u:group)if(u.command!=null&&u.command.navigation()!=null&&u.command.navigation().plan!=null){var plan=u.command.navigation().plan;automaticHead=plan.head;automaticEnd=plan.end;head=automaticHead.carrier.getUniqueId();break;}
            var distance=new HashMap<UUID,Integer>();distance.put(head,0);queue.add(head);
            while(!queue.isEmpty()){var id=queue.remove();for(var n:graph.getOrDefault(id,Set.of()))if(!distance.containsKey(n)){distance.put(n,distance.get(id)+1);queue.add(n);}}
            var travels=new HashMap<UUID,Vector3d>();
            for(var u:group)travels.computeIfAbsent(u.carrier.getUniqueId(),id->{
                var h=new Vector3d(0,0,1).rotate(pipe.readPose(u.bogie,new Pose3d()).orientation());
                boolean back=BranchSelect.reverseTravel(pipe.getLinearVelocity(u.bogie,new Vector3d()).dot(h),VehicleTraction.requestedSpeed(u),u.reverseTravel);return h.mul(back?-1:1);
            });
            group.sort((a,b)->{int cmp=Integer.compare(distance.getOrDefault(a.carrier.getUniqueId(),9999),distance.getOrDefault(b.carrier.getUniqueId(),9999));if(cmp!=0)return cmp;
                if(a.carrier!=b.carrier)return a.carrier.getUniqueId().compareTo(b.carrier.getUniqueId());
                var delta=VehiclePhysics.world(b.bogie,b.localCenter).sub(VehiclePhysics.world(a.bogie,a.localCenter));
                int along=Double.compare(delta.dot(travels.get(a.carrier.getUniqueId())),0);return along!=0?along:a.bogie.getUniqueId().compareTo(b.bogie.getUniqueId());
            });
            var previous=old.get(head);if(previous==null)for(var u:group)if(old.containsKey(u.carrier.getUniqueId())){previous=old.get(u.carrier.getUniqueId());break;}
            Context context;
            if(previous!=null&&previous.memory.members().equals(axleIds))context=previous;
            else{context=new Context(previous==null?new RouteMemory(axleIds):previous.memory.copyFor(axleIds),level,seedId);if(previous!=null)for(var a:axleIds)if(previous.anchors.containsKey(a))context.anchors.put(a,previous.anchors.get(a));}
            for(var passage:context.memory.active())RailwaysCompat.hold(level,passage,context.identity);
            context.automatic=null;for(var u:group)if(u.command!=null&&u.command.navigation()!=null){context.automatic=u.command.navigation();break;}
            context.intent=0;for(var u:group)if(u.driver!=null&&u.driver.active&&!u.driver.observing){context.intent=level.getGameTime()-u.driver.steerAt<=30?u.driver.steer:0;break;}
            for(var id:members)next.put(id,context);
            if(automaticHead!=null&&group.remove(automaticHead))group.addFirst(automaticHead);
            VehiclePhysics.Unit first=null;for(var u:group)if(!u.detached){first=u;break;}
            int leading=0;if(first!=null){var h=new Vector3d(0,0,1).rotate(pipe.readPose(first.bogie,new Pose3d()).orientation());leading=BranchSelect.reverseTravel(pipe.getLinearVelocity(first.bogie,new Vector3d()).dot(h),VehicleTraction.requestedSpeed(first),first.reverseTravel)?1:0;if(automaticHead==first)leading=automaticEnd;context.head=first.samples[leading];}
            for(var u:group)output.add(new Plan(u,new Cursor(context,new RouteMemory.Axle(u.bogie.getUniqueId(),0),u==first&&leading==0,u.mount.gauge),new Cursor(context,new RouteMemory.Axle(u.bogie.getUniqueId(),1),u==first&&leading==1,u.mount.gauge)));
        }
        STATES.put(level,next);CACHE.put(level,new Cached(level.getGameTime(),output));return output;
    }
    static void unload(ServerLevel level){STATES.remove(level);CACHE.remove(level);}
    static void topologyChanged(ServerLevel level){CACHE.remove(level);}
}
