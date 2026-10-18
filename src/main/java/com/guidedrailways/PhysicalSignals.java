package com.guidedrailways;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.*;

/** Create block sections shared by real physical consists, manual consists and native trains. */
public final class PhysicalSignals {
    private record TraceKey(UUID graph,int a,int b,int c,int d){}
    private record Bridge(List<ScheduleRoute.Edge> edges){}
    private static final class State {
        final SignalOccupancy ledger=new SignalOccupancy();
        final Map<UUID,UUID> owners=new HashMap<>();
        final Map<TraceKey,Bridge> traces=new HashMap<>();
        final Map<UUID,Set<UUID>> lastKnown=new HashMap<>();
        final Map<UUID,AutomaticNavigation> networks=new HashMap<>();
        final Map<UUID,Stop> stops=new HashMap<>();
    }
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    private record Boundary(SignalBoundary signal,TrackNode to,TrackEdge edge,double distance,boolean chain){}
    private record Stop(UUID signal,String reason){}
    private PhysicalSignals(){}
    public static boolean nativeBlocked(UUID group,UUID boundary){
        for(var state:STATES.values())if(state.ledger.nativeBlocked(group,boundary))return true;
        return false;
    }
    private static AutomaticNavigation network(State state,ServerLevel level,UUID graph){return state.networks.computeIfAbsent(graph,k->new AutomaticNavigation(level,k));}
    static void tick(ServerLevel level,Collection<VehiclePhysics.Unit> input){
        var s=STATES.computeIfAbsent(level,k->new State());s.networks.clear();s.owners.clear();
        var units=new ArrayList<VehiclePhysics.Unit>();
        for(var u:input)if(!u.carrier.isRemoved()&&!u.bogie.isRemoved()&&!u.mount.isRemoved())units.add(u);
        var adjacency=new HashMap<UUID,Set<UUID>>();
        for(var u:units)adjacency.computeIfAbsent(u.carrier.getUniqueId(),k->new HashSet<>());
        for(var link:CouplerConnections.bodyLinks(level)){
            var a=link.a().getUniqueId();var b=link.b().getUniqueId();
            adjacency.computeIfAbsent(a,k->new HashSet<>()).add(b);adjacency.computeIfAbsent(b,k->new HashSet<>()).add(a);
        }
        for(var seed:adjacency.keySet()){
            if(s.owners.containsKey(seed))continue;
            var members=new HashSet<UUID>();var queue=new ArrayDeque<UUID>();members.add(seed);queue.add(seed);
            while(!queue.isEmpty())for(var next:adjacency.getOrDefault(queue.remove(),Set.of()))if(members.add(next))queue.add(next);
            var owner=members.stream().min(UUID::compareTo).orElseThrow();for(var id:members)s.owners.put(id,owner);
        }
        var occupied=new HashMap<UUID,Set<UUID>>();var running=new HashSet<UUID>();var liveBodies=new HashSet<UUID>();
        var anchors=new HashMap<UUID,List<TrackNetwork.Sample>>();
        var pipe=SubLevelPhysicsSystem.get(level).getPipeline();
        var consoles=new HashSet<UUID>();
        for(var u:units){
            UUID body=u.carrier.getUniqueId(),owner=s.owners.get(body);liveBodies.add(body);
            if(u.driver!=null&&u.driver.schedule.running)running.add(owner);
            if(u.driver!=null)consoles.add(u.driver.consoleId);
            var groups=occupied.computeIfAbsent(owner,k->new HashSet<>());
            var samples=anchors.computeIfAbsent(body,k->new ArrayList<>());
            var heading=new Vector3d(0,0,1).rotate(pipe.readPose(u.bogie,new Pose3d()).orientation());
            if(!u.detached)for(int i=0;i<2;i++){
                var old=u.samples[i];var sample=TrackNetwork.nearest(level,VehiclePhysics.world(u.bogie,u.axle(i)),heading,old==null?null:old.path(),1);
                if(sample!=null){samples.add(sample);mark(sample.path(),sample.t()*sample.path().edge().getLength(),sample.t()*sample.path().edge().getLength(),groups);}
            }
            if(samples.isEmpty())groups.addAll(s.lastKnown.getOrDefault(body,Set.of()));
        }
        // Connect wheel supports within each carriage, then between physically linked carriages.
        for(var entry:anchors.entrySet()){
            var groups=occupied.get(s.owners.get(entry.getKey()));var samples=entry.getValue();
            connectTree(s,level,samples,groups);
            if(!samples.isEmpty()){
                var carrier=units.stream().filter(u->u.carrier.getUniqueId().equals(entry.getKey())).findFirst().orElseThrow().carrier;
                var reference=samples.getFirst();var axis=new Vector3d(reference.tangent()).normalize();
                var bounds=carrier.boundingBox();double lo=Double.POSITIVE_INFINITY,hi=-lo;
                for(double x:new double[]{bounds.minX(),bounds.maxX()})for(double y:new double[]{bounds.minY(),bounds.maxY()})for(double z:new double[]{bounds.minZ(),bounds.maxZ()}){
                    double d=new Vector3d(x,y,z).sub(reference.position()).dot(axis);lo=Math.min(lo,d);hi=Math.max(hi,d);
                }
                TrackNetwork.Sample front=reference,rear=reference;double max=0,min=0;
                for(var sample:samples){double d=new Vector3d(sample.position()).sub(reference.position()).dot(axis);if(d>max){max=d;front=sample;}if(d<min){min=d;rear=sample;}}
                reach(s,level,front,axis,Math.max(.5,hi-max)+.15,groups);
                reach(s,level,rear,new Vector3d(axis).negate(),Math.max(.5,min-lo)+.15,groups);
            }
            s.lastKnown.put(entry.getKey(),Set.copyOf(groups));
        }
        var consistAnchors=new HashMap<UUID,List<TrackNetwork.Sample>>();
        anchors.forEach((body,samples)->consistAnchors.computeIfAbsent(s.owners.get(body),k->new ArrayList<>()).addAll(samples));
        consistAnchors.forEach((owner,samples)->connectTree(s,level,samples,occupied.get(owner)));
        var bareBodies=new HashSet<UUID>();
        for(var link:CouplerConnections.bodyLinks(level))for(var body:List.of(link.a(),link.b())){
            if(anchors.containsKey(body.getUniqueId())||!bareBodies.add(body.getUniqueId()))continue;
            var owner=s.owners.get(body.getUniqueId());var samples=consistAnchors.getOrDefault(owner,List.of());if(samples.isEmpty())continue;
            var bounds=body.boundingBox();var centre=bounds.center(new Vector3d());
            var closest=samples.stream().min(Comparator.comparingDouble(p->p.position().distanceSquared(centre))).orElseThrow();
            var axis=new Vector3d(closest.tangent());double lo=0,hi=0;
            for(double x:new double[]{bounds.minX(),bounds.maxX()})for(double y:new double[]{bounds.minY(),bounds.maxY()})for(double z:new double[]{bounds.minZ(),bounds.maxZ()}){
                double d=new Vector3d(x,y,z).sub(closest.position()).dot(axis);lo=Math.min(lo,d);hi=Math.max(hi,d);
            }
            reach(s,level,closest,axis,hi+.15,occupied.get(owner));reach(s,level,closest,axis.negate(),-lo+.15,occupied.get(owner));
        }
        s.lastKnown.keySet().retainAll(liveBodies);
        s.stops.keySet().retainAll(consoles);
        if(s.traces.size()>2048)s.traces.clear();
        s.ledger.refresh(occupied,running,level.getGameTime());
    }
    private static void mark(TrackNetwork.Path path,double low,double high,Set<UUID> groups){
        double length=path.edge().getLength();low=Math.clamp(low,0,length);high=Math.clamp(high,low,length);
        var cuts=new ArrayList<Double>();cuts.add(low);cuts.add(high);
        for(var point:path.edge().getEdgeData().getPoints())if(point instanceof SignalBoundary){double d=point.getLocationOn(path.edge());if(d>=low&&d<=high){cuts.add(Math.max(low,d-1e-5));cuts.add(Math.min(high,d+1e-5));}}
        cuts.sort(Double::compare);
        for(int i=0;i<cuts.size();i++){
            double d=cuts.get(i);if(i>0)addGroup(path,(d+cuts.get(i-1))*.5,groups);addGroup(path,d,groups);
        }
    }
    private static void addGroup(TrackNetwork.Path path,double offset,Set<UUID> groups){var id=path.edge().getEdgeData().getGroupAtPosition(path.network(),offset);if(id!=null&&!id.equals(EdgeData.passiveGroup))groups.add(id);}
    private static void connectTree(State s,ServerLevel level,List<TrackNetwork.Sample> samples,Set<UUID> groups){
        int size=samples.size();if(size==0)return;
        var joined=new boolean[size];var distance=new double[size];var parent=new int[size];
        Arrays.fill(distance,Double.POSITIVE_INFINITY);distance[0]=0;
        for(int count=0;count<size;count++){
            int next=-1;for(int i=0;i<size;i++)if(!joined[i]&&(next<0||distance[i]<distance[next]))next=i;
            if(next<0)break;joined[next]=true;
            if(count>0)bridge(s,level,samples.get(parent[next]),samples.get(next),groups);
            for(int i=0;i<size;i++)if(!joined[i]){
                double d=samples.get(next).position().distanceSquared(samples.get(i).position());
                if(d<distance[i]){distance[i]=d;parent[i]=next;}
            }
        }
    }
    private static void bridge(State s,ServerLevel level,TrackNetwork.Sample a,TrackNetwork.Sample b,Set<UUID> groups){
        if(!a.path().graph().equals(b.path().graph()))return;
        if(a.path()==b.path()){mark(a.path(),Math.min(a.t(),b.t())*a.path().edge().getLength(),Math.max(a.t(),b.t())*a.path().edge().getLength(),groups);return;}
        var n=network(s,level,a.path().graph());var key=new TraceKey(n.graph,a.path().a(),a.path().b(),b.path().a(),b.path().b());
        var cached=s.traces.get(key);ScheduleRoute.Route best=null;
        double limit=Math.max(8,a.position().distance(b.position())*2+8);
        if(cached==null||cached.edges.stream().anyMatch(e->!n.paths.containsKey(e)||!TrackNetwork.live(level,n.paths.get(e)))){
            for(boolean ar:new boolean[]{false,true})for(boolean br:new boolean[]{false,true}){
                var from=new ScheduleRoute.Edge(ar?a.path().b():a.path().a(),ar?a.path().a():a.path().b());
                var to=new ScheduleRoute.Edge(br?b.path().b():b.path().a(),br?b.path().a():b.path().b());
                var route=ScheduleRoute.find(n,from,n.offset(a,from),List.of(new ScheduleRoute.Goal(new UUID(0,0),"",to,n.offset(b,to))),0,limit);
                if(route!=null&&route.distance()<=limit&&(best==null||route.distance()<best.distance()))best=route;
            }
            if(best==null){
                // A broken connection beneath a carriage must not silently leave a free section.
                for(var signal:a.path().network().getPoints(EdgePointType.SIGNAL))for(boolean side:new boolean[]{false,true}){
                    var group=signal.groups.get(side);if(group!=null)groups.add(group);
                }
                return;
            }
            cached=new Bridge(best.edges());s.traces.put(key,cached);
        }
        for(int i=0;i<cached.edges.size();i++){
            var edge=cached.edges.get(i);var path=n.paths.get(edge);double lo=i==0?n.offset(a,edge):0,hi=i==cached.edges.size()-1?n.offset(b,edge):n.length(edge);
            if(path.a()!=edge.from()){double old=lo;lo=n.length(edge)-hi;hi=n.length(edge)-old;}mark(path,Math.min(lo,hi),Math.max(lo,hi),groups);
        }
    }
    private record Reach(ScheduleRoute.Edge edge,double offset,double distance){}
    private static void reach(State s,ServerLevel level,TrackNetwork.Sample sample,Vector3d heading,double distance,Set<UUID> groups){
        var n=network(s,level,sample.path().graph());var first=n.direction(sample,heading);var queue=new ArrayDeque<Reach>();queue.add(new Reach(first,n.offset(sample,first),Math.min(256,distance)));int visits=0;
        while(!queue.isEmpty()&&visits++<256){
            var v=queue.remove();var path=n.paths.get(v.edge);if(path==null)continue;
            double hi=Math.min(n.length(v.edge),v.offset+v.distance),lo=v.offset;
            mark(path,path.a()==v.edge.from()?lo:n.length(v.edge)-hi,path.a()==v.edge.from()?hi:n.length(v.edge)-lo,groups);
            double rest=v.distance-(hi-lo);if(rest>1e-5)for(var exit:n.exits(v.edge))queue.add(new Reach(exit,0,rest));
        }
    }
    private static Set<UUID> conflicts(ServerLevel level,UUID group){
        var result=new HashSet<UUID>();var queue=new ArrayDeque<UUID>();if(group!=null){result.add(group);queue.add(group);}
        var all=Create.RAILWAYS.sided(level).signalEdgeGroups;
        while(!queue.isEmpty()){
            var current=all.get(queue.remove());if(current==null)continue;
            for(var other:current.intersectingResolved)if(result.add(other.id))queue.add(other.id);
            for(var id:current.intersecting.values())if(all.containsKey(id)&&result.add(id))queue.add(id);
        }
        return result;
    }
    private static List<Boundary> ahead(AutomaticTrains.Plan plan,int leg,double offset){
        var out=new ArrayList<Boundary>();double distance=-offset;
        for(int i=leg;i<plan.route.edges().size();i++){
            var e=plan.route.edges().get(i);var path=plan.network.paths.get(e);var from=path.network().getNode(e.from());var to=path.network().getNode(e.to());var edge=path.network().getConnectionsFrom(from).get(to);
            double end=i==plan.route.edges().size()-1?plan.route.goal().offset():edge.getLength();
            for(var point:edge.getEdgeData().getPoints())if(point instanceof SignalBoundary signal){
                double d=signal.getLocationOn(edge);if(d>end+1e-6||i==leg&&d<offset-1e-5)continue;
                out.add(new Boundary(signal,to,edge,distance+d,signal.types.get(signal.isPrimary(to))==SignalBlock.SignalType.CROSS_SIGNAL));
            }
            distance+=edge.getLength();
        }
        out.sort(Comparator.comparingDouble(Boundary::distance));return out;
    }
    static double nose(VehiclePhysics.Unit head,Vector3d point,Vector3d travel){
        // Use the route's actual leading carriage, even when a tail folds alongside it on a loop.
        var bounds=head.carrier.boundingBox();double result=.5;
        for(double x:new double[]{bounds.minX(),bounds.maxX()})for(double y:new double[]{bounds.minY(),bounds.maxY()})for(double z:new double[]{bounds.minZ(),bounds.maxZ()})result=Math.max(result,new Vector3d(x,y,z).sub(point).dot(travel));
        return result;
    }
    /** Distance to the first denied boundary, or infinity. Claims avoid simultaneous green entry. */
    static double limit(ServerLevel level,DriverConsoleEntity console,AutomaticTrains.Plan plan,double offset,double horizon){
        var s=STATES.get(level);var body=DriverControls.body(console);if(s==null||body==null)return 0;
        var owner=s.owners.get(body.getUniqueId());if(owner==null)return 0;
        var boundaries=ahead(plan,plan.leg,offset);var all=Create.RAILWAYS.sided(level).signalEdgeGroups;
        for(int i=0;i<boundaries.size();i++){
            var first=boundaries.get(i);if(first.distance>horizon)break;
            var requested=new LinkedHashMap<UUID,UUID>();String denied=null;boolean exitFound=!first.chain;
            for(int j=i;j<boundaries.size();j++){
                var b=boundaries.get(j);UUID id=b.signal.getGroup(b.to);
                if(!b.signal.canNavigateVia(b.to)){denied="wrong_direction";break;}
                if(b.signal.isForcedRed(b.to)){denied="forced_red";break;}
                if(Objects.equals(b.signal.groups.getFirst(),b.signal.groups.getSecond())){denied="invalid_boundary";break;}
                if(id==null||!all.containsKey(id)){denied="unresolved_group";break;}
                for(var group:conflicts(level,id))requested.putIfAbsent(group,b.signal.id);
                if(!b.chain){exitFound=true;break;}
            }
            if(denied==null&&!exitFound)denied="no_chain_exit";
            if(denied==null&&!s.ledger.claim(owner,requested,level.getGameTime(),id->{var g=all.get(id);return g==null||!g.trains.isEmpty()||g.reserved!=null;}))denied="section_occupied_or_reserved";
            if(denied!=null){
                var stop=new Stop(first.signal.id,denied);
                if(!stop.equals(s.stops.put(console.consoleId,stop)))org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[PhysicalSignal] console={} signal={} reason={} distance={} sections={}",console.getBlockPos(),first.signal.id,denied,first.distance,requested.keySet());
                return first.distance;
            }
            // Reserve only within the stopping horizon; a later denied block still limits braking now.
        }
        var old=s.stops.remove(console.consoleId);
        if(old!=null)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[PhysicalSignal] console={} signal={} released=true",console.getBlockPos(),old.signal);
        return Double.POSITIVE_INFINITY;
    }
    static void unload(ServerLevel level){STATES.remove(level);}
}
