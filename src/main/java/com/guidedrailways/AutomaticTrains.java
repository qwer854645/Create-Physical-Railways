package com.guidedrailways;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.*;

/** A controller issues bounded drive demands to all bogies in one live coupled consist. */
final class AutomaticTrains {
    record Control(DriverConsoleEntity owner,ScheduleController navigation,int sign,double ceiling,int notch,boolean emergency,boolean hold){}
    static final class Plan {
        final AutomaticNavigation network;final ScheduleRoute.Route route;final VehiclePhysics.Unit head;final int end;
        final Map<UUID,Integer> signs;final int direction;int leg;long nextLoaded;boolean loaded;
        Plan(AutomaticNavigation n,ScheduleRoute.Route r,VehiclePhysics.Unit h,int end,Map<UUID,Integer> signs,int direction){network=n;route=r;head=h;this.end=end;this.signs=signs;this.direction=direction;}
        int choose(TrackNetwork.Path path,int node,int from,int fallback){
            if(!path.graph().equals(network.graph))return fallback;
            int result=route.next(from,node,leg);return result<0?fallback:result;
        }
    }
    record Basis(Map<UUID,Integer> signs,VehiclePhysics.Unit head,int end){}
    private static Vector3d heading(VehiclePhysics.Unit u){return new Vector3d(0,0,1).rotate(SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline().readPose(u.bogie,new Pose3d()).orientation());}
    private static Vector3d consoleHeading(DriverConsoleEntity e){var f=e.getBlockState().getValue(DriverConsoleBlock.FACING);var body=DriverControls.body(e);return new Vector3d(f.getStepX(),0,f.getStepZ()).rotate(SubLevelPhysicsSystem.get(body.getLevel()).getPipeline().readPose(body,new Pose3d()).orientation());}
    static void tick(ServerLevel level,Collection<VehiclePhysics.Unit> input,Collection<DriverConsoleEntity> consoles){
        var previous=new HashSet<VehiclePhysics.Unit>();var units=new ArrayList<VehiclePhysics.Unit>();
        for(var u:input){if(u.automatic!=null)previous.add(u);u.automatic=null;if(!u.bogie.isRemoved()&&!u.carrier.isRemoved()&&!u.mount.isRemoved()&&u.lastSeen+2>=level.getGameTime())units.add(u);}
        var links=CouplerConnections.bodyLinks(level);var graph=new HashMap<UUID,Set<UUID>>();
        for(var u:units)graph.computeIfAbsent(u.carrier.getUniqueId(),k->new HashSet<>());
        for(var l:links){var a=l.a().getUniqueId();var b=l.b().getUniqueId();graph.computeIfAbsent(a,k->new HashSet<>()).add(b);graph.computeIfAbsent(b,k->new HashSet<>()).add(a);}
        var visited=new HashSet<UUID>();var processed=new HashSet<ScheduleController>();
        for(var seed:units){
            var root=seed.carrier.getUniqueId();if(!visited.add(root))continue;
            var ids=new HashSet<UUID>();var queue=new ArrayDeque<UUID>();ids.add(root);queue.add(root);
            while(!queue.isEmpty())for(var id:graph.getOrDefault(queue.remove(),Set.of()))if(ids.add(id)){visited.add(id);queue.add(id);}
            var group=new ArrayList<VehiclePhysics.Unit>();for(var u:units)if(ids.contains(u.carrier.getUniqueId()))group.add(u);
            var stations=new ArrayList<DriverConsoleEntity>();for(var e:consoles){var b=DriverControls.body(e);if(b!=null&&ids.contains(b.getUniqueId()))stations.add(e);}
            stations.sort(Comparator.comparingLong(e->e.getBlockPos().asLong()));
            var master=group.getFirst().driver;if(master==null)continue;
            var controller=master.schedule;processed.add(controller);stats(master,group);
            if(master.emergency){if(controller.present())controller.pause(ScheduleController.EMERGENCY);hold(group,controller,true);continue;}
            if(controller.resumePending){
                var restored=new HashSet<UUID>();for(var u:group)restored.add(u.bogie.getUniqueId());
                if(!controller.restore(level,restored)){hold(group,controller,false);continue;}
            }
            if(!controller.present()){
                var orientation=basis(master,group,links,master.direction==0?1:master.direction);
                if(orientation==null){hold(group,controller,false);continue;}
                bind(group,master,null,orientation.signs,master.direction==0?0:available(group,master),master.notch,false,!master.enabled);continue;
            }
            if(master.active&&!master.observing)controller.manual(master);
            if(controller.manualOverride){
                var source=controller.manualSource;
                if(source==null||source.isRemoved()||DriverControls.body(source)==null){controller.pause(ScheduleController.PAUSED);hold(group,controller,false);continue;}
                stats(source,group);
                var basis=basis(source,group,links,source.direction==0?1:source.direction);
                if(basis==null){controller.pause(ScheduleController.CONFLICT);hold(group,controller,false);continue;}
                bind(group,source,null,basis.signs,source.direction==0?0:available(group,source),source.notch,false,!source.enabled);continue;
            }
            if(!controller.running){hold(group,controller,false);continue;}
            var members=new HashSet<UUID>();for(var u:group)members.add(u.bogie.getUniqueId());
            if(controller.members==null)controller.members=Set.copyOf(members);
            else if(!controller.members.equals(members)){controller.pause(ScheduleController.CHANGED);hold(group,controller,false);continue;}
            if(group.stream().anyMatch(u->u.detached)){controller.pause(ScheduleController.TRACK_MISSING);hold(group,controller,false);continue;}
            if(controller.releaseBrakes){for(var u:group)brake(u,false);controller.releaseBrakes=false;}
            double cap=available(group,master);if(cap<=0){controller.state(ScheduleController.NO_POWER);hold(group,controller,false);continue;}
            try{drive(level,group,links,controller,cap);}catch(RuntimeException error){
                controller.pause(ScheduleController.UNSUPPORTED);hold(group,controller,false);
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[AutomaticTrain] paused after invalid schedule/route console={}",controller.owner.getBlockPos(),error);
            }
        }
        for(var console:consoles)if(console.schedule.running&&!processed.contains(console.schedule))console.schedule.pause(ScheduleController.TRACK_MISSING);
        // A split or removed controller must not leave its former motorised trailers running.
        for(var u:previous)if(u.automatic==null&&!u.bogie.isRemoved()&&!u.mount.isRemoved())brake(u,true);
    }
    private static void stats(DriverConsoleEntity e,List<VehiclePhysics.Unit> group){
        e.available=available(group,e);e.supports=group.size();e.powered=0;e.guided=0;e.mountBrakes=0;
        for(var u:group){if(Math.abs(u.mount.getSpeed())>.001)e.powered++;if(!u.detached)e.guided++;if(u.mount.braking)e.mountBrakes++;}
    }
    private static void brake(VehiclePhysics.Unit u,boolean value){if(u.mount.braking!=value){u.mount.braking=value;u.mount.setChanged();u.mount.sendData();}}
    private static double available(List<VehiclePhysics.Unit> group,DriverConsoleEntity console){double cap=Double.POSITIVE_INFINITY;for(var u:group)if(Math.abs(u.mount.getSpeed())>.001)cap=Math.min(cap,console.targetSpeed(u.mount.getSpeed()));return Double.isFinite(cap)?cap:0;}
    private static double brakeFraction(List<VehiclePhysics.Unit> group,List<CouplerConnections.BodyLink> links){
        var supported=new HashMap<UUID,ServerSubLevel>();double wheels=0;
        for(var u:group){supported.put(u.carrier.getUniqueId(),u.carrier);wheels+=u.bogie.getMassTracker().getMass();}
        double supportedMass=wheels;for(var b:supported.values())supportedMass+=b.getMassTracker().getMass();
        var all=new HashMap<>(supported);boolean changed;
        do{changed=false;for(var l:links)if(all.containsKey(l.a().getUniqueId())||all.containsKey(l.b().getUniqueId())){if(all.putIfAbsent(l.a().getUniqueId(),l.a())==null)changed=true;if(all.putIfAbsent(l.b().getUniqueId(),l.b())==null)changed=true;}}while(changed);
        double total=wheels;for(var b:all.values())total+=b.getMassTracker().getMass();
        return Math.clamp(supportedMass/Math.max(.001,total),.001,1);
    }
    private static double maximumSpeed(List<VehiclePhysics.Unit> group){double speed=0;for(var u:group)speed=Math.max(speed,SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline().getLinearVelocity(u.bogie,new Vector3d()).length());return speed;}
    private static void hold(List<VehiclePhysics.Unit> group,ScheduleController c,boolean emergency){c.visual(-4,c.owner.direction);for(var u:group)u.automatic=new Control(c.owner,null,1,0,-4,emergency,true);}
    private static void bind(List<VehiclePhysics.Unit> group,DriverConsoleEntity owner,ScheduleController navigation,Map<UUID,Integer> signs,double cap,int notch,boolean emergency,boolean hold){if(navigation!=null)navigation.visual(notch,navigation.plan.direction);for(var u:group)u.automatic=new Control(owner,navigation,signs.getOrDefault(u.bogie.getUniqueId(),1),cap,notch,emergency,hold);}
    static Basis basis(DriverConsoleEntity console,List<VehiclePhysics.Unit> group,List<CouplerConnections.BodyLink> links,int direction){
        var root=DriverControls.body(console);if(root==null)return null;
        var axes=new HashMap<UUID,Vector3d>();var centers=new HashMap<UUID,Vector3d>();var counts=new HashMap<UUID,Integer>();
        for(var u:group){var id=u.carrier.getUniqueId();axes.putIfAbsent(id,heading(u));centers.computeIfAbsent(id,k->new Vector3d()).add(VehiclePhysics.world(u.bogie,u.localCenter));counts.merge(id,1,Integer::sum);}
        for(var id:centers.keySet())centers.get(id).div(counts.get(id));
        for(var l:links)for(var b:List.of(l.a(),l.b())){var id=b.getUniqueId();axes.putIfAbsent(id,new Vector3d(0,0,1).rotate(SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b,new Pose3d()).orientation()));centers.putIfAbsent(id,VehiclePhysics.world(b,b.getMassTracker().getCenterOfMass()));}
        axes.put(root.getUniqueId(),consoleHeading(console));
        var orientedLinks=new ArrayList<ConsistDirection.Link>();
        for(var l:links){
            var a=l.a().getUniqueId();var b=l.b().getUniqueId();
            var pa=VehiclePhysics.world(l.a(),l.anchorA()).sub(centers.get(a));var pb=VehiclePhysics.world(l.b(),l.anchorB()).sub(centers.get(b));
            orientedLinks.add(new ConsistDirection.Link(a,b,pa.dot(axes.get(a))<0?-1:1,pb.dot(axes.get(b))<0?-1:1));
        }
        var orientations=ConsistDirection.orient(root.getUniqueId(),direction,orientedLinks);if(orientations==null)return null;
        var signs=new HashMap<UUID,Integer>();
        for(var u:group){var id=u.carrier.getUniqueId();var travel=new Vector3d(axes.get(id)).mul(orientations.getOrDefault(id,direction));signs.put(u.bogie.getUniqueId(),heading(u).dot(travel)<0?-1:1);}
        UUID front=null;
        for(var id:orientations.keySet()){
            var travel=new Vector3d(axes.get(id)).mul(orientations.get(id));int degree=0;boolean leading=true;
            for(var l:links)if(l.a().getUniqueId().equals(id)||l.b().getUniqueId().equals(id)){
                degree++;boolean a=l.a().getUniqueId().equals(id);var socket=VehiclePhysics.world(a?l.a():l.b(),a?l.anchorA():l.anchorB());
                if(socket.sub(centers.get(id)).dot(travel)>0)leading=false;
            }
            if(degree<=1&&leading){if(front!=null)return null;front=id;}
        }
        if(front==null)return null;
        var distance=new HashMap<UUID,Integer>();var queue=new ArrayDeque<UUID>();distance.put(front,0);queue.add(front);
        while(!queue.isEmpty()){
            var id=queue.remove();for(var l:links){UUID next=l.a().getUniqueId().equals(id)?l.b().getUniqueId():l.b().getUniqueId().equals(id)?l.a().getUniqueId():null;
                if(next!=null&&!distance.containsKey(next)){distance.put(next,distance.get(id)+1);queue.add(next);}}
        }
        VehiclePhysics.Unit head=null;double best=-Double.MAX_VALUE;int hops=Integer.MAX_VALUE;
        for(var u:group){
            var id=u.carrier.getUniqueId();int d=distance.getOrDefault(id,Integer.MAX_VALUE);var travel=new Vector3d(axes.get(id)).mul(orientations.getOrDefault(id,direction));
            double rank=VehiclePhysics.world(u.bogie,u.localCenter).sub(centers.get(id)).dot(travel);
            if(d<hops||d==hops&&rank>best){head=u;hops=d;best=rank;}
        }
        if(head==null)return null;return new Basis(signs,head,signs.get(head.bogie.getUniqueId())>0?0:1);
    }
    private static TrackNetwork.Sample sample(ServerLevel level,VehiclePhysics.Unit u,int end){
        var current=u.samples[end];var p=VehiclePhysics.world(u.bogie,u.axle(end));return TrackNetwork.nearest(level,p,heading(u),current==null?null:current.path(),1);
    }
    private static Plan plan(ServerLevel level,List<VehiclePhysics.Unit> group,List<CouplerConnections.BodyLink> links,ScheduleController c){
        var instruction=c.instruction();if(instruction==null)return null;Plan best=null;
        boolean stopped=maximumSpeed(group)<.15;
        int forward=c.owner.actual<-.05?-1:1;
        for(int direction:new int[]{forward,-forward}){
            if(direction!=forward&&!stopped)continue;
            var basis=basis(c.owner,group,links,direction);if(basis==null)continue;var head=basis.head;var sample=sample(level,head,basis.end);if(sample==null)continue;
            var network=new AutomaticNavigation(level,sample.path().graph());var travel=new Vector3d(sample.tangent()).mul(basis.signs.get(head.bogie.getUniqueId()));var start=network.direction(sample,travel);
            var route=ScheduleRoute.find(network,start,network.offset(sample,start),network.goals(instruction,c.waiting?c.station:null),AutomaticDriveModel.STOP_TOLERANCE);
            if(route!=null&&(best==null||route.distance()<best.route.distance()-1e-6))best=new Plan(network,route,head,basis.end,basis.signs,direction);
        }
        return best;
    }
    private static void drive(ServerLevel level,List<VehiclePhysics.Unit> group,List<CouplerConnections.BodyLink> links,ScheduleController c,double cap){
        if(c.plan!=null&&!c.plan.network.live(c.plan.route)){c.plan=null;c.remaining=Double.NaN;c.waiting=false;c.station=null;c.resetConditions();c.nextPlan=0;}
        if(c.plan==null){
            if(level.getGameTime()<c.nextPlan){hold(group,c,false);return;}c.nextPlan=level.getGameTime()+10;
            c.plan=plan(level,group,links,c);
            if(c.plan==null){c.state(ScheduleController.NO_ROUTE);hold(group,c,false);return;}
            c.station=c.plan.route.goal().id();c.owner.setChanged();
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[AutomaticRoute] target={} distance={} edges={} leadingBogie={} end={}",c.plan.route.goal().name(),c.plan.route.distance(),c.plan.route.edges().size(),c.plan.head.bogie.getUniqueId(),c.plan.end);
        }
        var p=c.plan;var sample=sample(level,p.head,p.end);if(sample==null){c.pause(ScheduleController.TRACK_MISSING);hold(group,c,false);return;}
        int found=-1;
        for(int i=p.leg;i<=Math.min(p.leg+1,p.route.edges().size()-1);i++){var edge=p.route.edges().get(i);if(sameEdge(sample,edge)){found=i;break;}}
        if(found<0&&p.leg>0&&sameEdge(sample,p.route.edges().get(p.leg-1)))found=p.leg-1;
        if(found<0){c.pause(ScheduleController.NO_ROUTE);hold(group,c,false);return;}p.leg=found;
        var edge=p.route.edges().get(p.leg);double offset=p.network.offset(sample,edge);double remaining=p.route.remaining(p.network,p.leg,offset);
        c.remaining=remaining;
        if(level.getGameTime()>=p.nextLoaded){p.loaded=p.network.loaded(p.route,p.leg,offset);p.nextLoaded=level.getGameTime()+10;}
        if(!p.loaded){c.state(ScheduleController.UNLOADED);hold(group,c,false);return;}
        double maxSpeed=maximumSpeed(group);boolean arrived=AutomaticDriveModel.arrived(remaining,maxSpeed);
        if(c.waiting&&!arrived){c.waiting=false;c.resetConditions();}
        if(arrived){
            hold(group,c,false);c.stableTicks++;
            if(c.stableTicks>=6){c.waiting=true;c.state(ScheduleController.WAITING);if(c.tickConditions(level))c.advance();}
            return;
        }
        c.stableTicks=0;
        if(remaining<-AutomaticDriveModel.STOP_TOLERANCE){c.pause(ScheduleController.OVERSHOOT);hold(group,c,false);return;}
        double speed=SubLevelPhysicsSystem.get(level).getPipeline().getLinearVelocity(p.head.bogie,new Vector3d()).dot(sample.tangent())*p.signs.get(p.head.bogie.getUniqueId());
        double braking=TrainConfig.BRAKING.get()*brakeFraction(group,links);
        double nose=PhysicalSignals.nose(p.head,sample.position(),new Vector3d(sample.tangent()).mul(p.signs.get(p.head.bogie.getUniqueId())));
        double horizon=nose+cap*cap/Math.max(.01,braking)+cap*.3+8;
        double signal=PhysicalSignals.limit(level,c.owner,p,offset,horizon);
        double signalRoom=signal-nose-.5;
        boolean waitingSignal=Double.isFinite(signal)&&signalRoom<remaining;
        var demand=AutomaticDriveModel.demand(waitingSignal?signalRoom:remaining,speed,cap,braking);
        c.state(waitingSignal?ScheduleController.SIGNAL_WAIT:ScheduleController.TRAVELLING);bind(group,c.owner,c,p.signs,demand.ceiling(),demand.notch(),false,false);
        if(level.getGameTime()%20==0)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[AutomaticDrive] target={} remaining={} speed={} ceiling={} notch={} bogies={} signalRoom={} nose={}",p.route.goal().name(),remaining,speed,demand.ceiling(),demand.notch(),group.size(),signalRoom,nose);
    }
    private static boolean sameEdge(TrackNetwork.Sample s,ScheduleRoute.Edge e){return s.path().a()==e.from()&&s.path().b()==e.to()||s.path().b()==e.from()&&s.path().a()==e.to();}
}
