package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;
import org.joml.Matrix3d;
import java.util.*;

/** Read-only Create graph adapter. It never creates blocks, rail colliders or train entities. */
public final class TrackNetwork {
    public record Path(UUID graph,int a,int b,TrackGraph network,TrackEdge edge) {
        boolean supports(RailGauge gauge){return gauge.accepts(edge.getTrackMaterial().trackType.id.toString());}
        Vector3d at(double t){var v=edge.getPosition(network,t);return new Vector3d(v.x,v.y,v.z);}
        boolean adjacent(Path p){return graph.equals(p.graph)&&(a==p.a||a==p.b||b==p.a||b==p.b);}
    }
    public record Sample(Path path,double t,Vector3d position,Vector3d tangent,Vector3d up){
        double lateralCurvature(){
            if(!path.edge.isTurn())return 0;
            double span=.25/Math.max(.01,path.edge.getLength()),lo=Math.max(0,t-span),hi=Math.min(1,t+span);
            var a=PathGeometry.tangent(path::at,lo);var b=PathGeometry.tangent(path::at,hi);
            return DerailmentModel.lateralCurvature(a,b,tangent,up,path.at(lo).distance(path.at(hi)));
        }
        Quaterniond orientation(){var x=new Vector3d(tangent);var y=new Vector3d(up);var z=new Vector3d(x).cross(y).normalize();y.set(z).cross(x).normalize();return new Quaterniond().setFromNormalized(new Matrix3d(x,y,z));}
    }
    interface RoutePolicy {
        int choose(Path incoming,int node,int from,int[] exits,int proposed,Vector3d position);
        boolean commit(Path incoming,int node,int from,int[] exits,int selected,Vector3d position);
        boolean blocked();
        void observe(Vector3dc position);
        BranchSelect.Steer steer();
        Sample leader();
        default boolean mayPass(Path incoming,int node,int from,int to){return true;}
        default RailGauge gauge(){return RailGauge.STANDARD;}
    }
    private record PathKey(UUID graph,int a,int b){}
    private static final class Index {
        long next; final Map<Long,Set<Path>> cells=new HashMap<>();final Map<PathKey,Path> paths=new HashMap<>();
    }
    private static final Map<ServerLevel,Index> INDEX=new WeakHashMap<>();
    private static long cell(double x,double z){return net.minecraft.world.level.ChunkPos.asLong((int)Math.floor(x/16),(int)Math.floor(z/16));}
    private static PathKey key(Path p){return new PathKey(p.graph,p.a,p.b);}
    private static PathKey key(UUID graph,int a,int b){return a<=b?new PathKey(graph,a,b):new PathKey(graph,b,a);}
    public static void refresh(ServerLevel level){
        var idx=INDEX.computeIfAbsent(level,l->new Index());if(level.getGameTime()<idx.next)return;idx.next=level.getGameTime()+10;
        long start=TrainConfig.PERFORMANCE_LOGGING.get()?System.nanoTime():0;
        var next=new HashMap<PathKey,Path>();boolean changed=false;
        for(var graph:Create.RAILWAYS.sided(level).trackNetworks.values())for(var loc:graph.getNodes()){
            if(!loc.dimension.equals(level.dimension()))continue;var node=graph.locateNode(loc);if(node==null)continue;
            for(var entry:graph.getConnectionsFrom(node).entrySet()){
                var other=entry.getKey();var edge=entry.getValue();if(node.getNetId()>=other.getNetId()||edge.isInterDimensional())continue;
                var key=new PathKey(graph.id,node.getNetId(),other.getNetId());var old=idx.paths.get(key);
                if(old!=null&&old.network==graph&&old.edge==edge)next.put(key,old);
                else{next.put(key,new Path(graph.id,key.a,key.b,graph,edge));changed=true;}
            }
        }
        if(next.size()!=idx.paths.size())changed=true;
        if(!changed){if(start!=0)PerfCounters.index(level,System.nanoTime()-start,false);return;}
        idx.paths.clear();idx.paths.putAll(next);idx.cells.clear();
        for(var path:idx.paths.values()){
            int n=Math.max(1,(int)Math.ceil(path.edge.getLength()/4));
            for(int i=0;i<=n;i++){var p=path.at((double)i/n);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)idx.cells.computeIfAbsent(cell(p.x+dx*16,p.z+dz*16),v->new HashSet<>()).add(path);}
        }
        if(start!=0)PerfCounters.index(level,System.nanoTime()-start,true);
    }
    static boolean live(ServerLevel level,Path path){var index=INDEX.get(level);return index!=null&&index.paths.get(key(path))==path;}
    static Collection<Path> paths(ServerLevel level){var index=INDEX.get(level);return index==null?List.of():List.copyOf(index.paths.values());}
    private static Sample project(Path path,Vector3dc query,Vector3dc heading,Double hint){
        double seed=0,span;
        if(!path.edge.isTurn()){
            var a=path.at(0);var delta=path.at(1).sub(a);double lengthSq=delta.lengthSquared();
            seed=lengthSq>1e-10?Math.clamp(new Vector3d(query).sub(a).dot(delta)/lengthSq,0,1):0;span=0;
        }else if(hint!=null){seed=hint;span=Math.clamp(.75/Math.max(.01,path.edge.getLength()),.005,.5);}
        else{
            double best=Double.POSITIVE_INFINITY;int n=Math.clamp((int)Math.ceil(path.edge.getLength()/2),8,512);
            for(int i=0;i<=n;i++){double t=(double)i/n;double d=path.at(t).distanceSquared(query);if(d<best){best=d;seed=t;}}
            span=1.0/n;
        }
        double lo=Math.max(0,seed-span),hi=Math.min(1,seed+span);
        for(int i=0;i<18&&hi>lo;i++){double a=(2*lo+hi)/3,b=(lo+2*hi)/3;if(path.at(a).distanceSquared(query)<path.at(b).distanceSquared(query))hi=b;else lo=a;}
        double t=(lo+hi)*.5;var p=path.at(t);if(path.at(0).distanceSquared(query)<p.distanceSquared(query)){t=0;p=path.at(0);}if(path.at(1).distanceSquared(query)<p.distanceSquared(query)){t=1;p=path.at(1);}
        var original=PathGeometry.tangent(path::at,t);var x=new Vector3d(original);
        if(x.dot(heading)<0)x.negate();var normal=path.edge.getNormal(path.network,t);var up=new Vector3d(normal.x,normal.y,normal.z).normalize();
        // Reject longitudinal overrun at a finite endpoint; no invisible guide beyond a broken rail.
        if(PathGeometry.beyondEndpoint(t,p,query,original,.08))return null;
        return new Sample(path,t,p,x,up);
    }
    /** Navigation observes the physics-owned edge; proximity must never select a neighbouring branch. */
    static Sample resample(ServerLevel level,Sample current,Vector3dc point,Vector3dc heading,double radius){
        if(current==null||!live(level,current.path))return null;
        var sample=project(current.path,point,heading,current.t);
        if(sample==null)sample=current; // Physics may not have published the node crossing yet.
        return sample.position.distanceSquared(point)<=radius*radius&&level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z))?sample:null;
    }
    public static Sample follow(ServerLevel level,Vector3dc point,Vector3dc heading,Sample previous,double radius){
        return follow(level,point,heading,previous,null,radius);
    }
    /**
     * Stay on the current edge while mid-span; at nodes choose by geometric turn angle
     * and explicit intent, with consist passage history taking priority over local guesses.
     */
    public static Sample follow(ServerLevel level,Vector3dc point,Vector3dc heading,Sample previous,Sample leader,double radius){
        return follow(level,point,heading,previous,leader,radius,false);
    }
    /** Heading fixes body/traction orientation; reverse changes traversal only. */
    public static Sample follow(ServerLevel level,Vector3dc point,Vector3dc heading,Sample previous,Sample leader,double radius,boolean reverse){
        return follow(level,point,heading,previous,leader,radius,reverse,null);
    }
    static Sample follow(ServerLevel level,Vector3dc point,Vector3dc heading,Sample previous,Sample leader,double radius,boolean reverse,RoutePolicy policy){
        if(policy!=null)policy.observe(point);
        PerfCounters.query(level);
        var idx=INDEX.get(level);if(previous!=null&&idx!=null){
            var live=idx.paths.get(key(previous.path));
            if(live!=null&&live.supports(policy==null?RailGauge.STANDARD:policy.gauge())){
                var sample=project(live,point,heading,previous.t);
                if(sample!=null&&sample.t>.0001&&sample.t<.9999&&sample.position.distanceSquared(point)<.09
                        &&sample.tangent.dot(heading)>.75&&level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))return sample;
                var branched=advance(level,idx,live,point,heading,previous,leader,radius,reverse,policy);
                if(branched!=null)return branched;
                if(policy!=null&&policy.blocked()&&previous.position.distanceSquared(point)<=radius*radius
                    &&level.hasChunkAt(BlockPos.containing(previous.position.x,previous.position.y,previous.position.z)))return previous;
                // A locked passage must never be bypassed by nearest-edge recovery.
                if(policy!=null)return sample!=null&&sample.position.distanceSquared(point)<=radius*radius&&level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z))?sample:null;
            }
            if(policy!=null)return null;
        }
        return nearest(level,point,heading,previous==null?null:previous.path,radius,policy==null?RailGauge.STANDARD:policy.gauge());
    }
    /** Create-style node transition: enumerate outgoing edges, then steer / follow. */
    private static Sample advance(ServerLevel level,Index idx,Path live,Vector3dc point,Vector3dc heading,Sample previous,Sample leader,double radius,boolean reverse,RoutePolicy policy){
        var increasing=PathGeometry.tangent(live::at,previous.t);
        boolean alongIncreasing=BranchSelect.exitsIncreasing(increasing,heading,reverse);
        double endT=alongIncreasing?1:0;
        if(Math.abs(previous.t-endT)>.12&&(previous.position.distanceSquared(live.at(endT))>.36))return null;
        int exitId=alongIncreasing?live.b:live.a,enterId=alongIncreasing?live.a:live.b;
        var exit=live.network.getNode(exitId);var enter=live.network.getNode(enterId);
        if(exit==null||enter==null)return null;
        var current=live.network.getConnectionsFrom(enter).get(exit);
        if(current==null)return null;
        var options=new ArrayList<Branch>();
        for(var entry:live.network.getConnectionsFrom(exit).entrySet()){
            if(entry.getKey()==enter||entry.getValue().isInterDimensional())continue;
            if(!RailwaysCompat.geometry(current,entry.getValue()))continue;
            var path=idx.paths.get(key(live.graph,exit.getNetId(),entry.getKey().getNetId()));
            if(path==null||!path.supports(policy==null?RailGauge.STANDARD:policy.gauge()))continue;
            options.add(new Branch(entry.getKey(),entry.getValue(),path));
        }
        if(options.isEmpty())return null;
        options.sort(Comparator.comparingInt(c->c.node.getNetId()));
        var incoming=PathGeometry.tangent(live::at,endT).mul(alongIncreasing?1:-1);
        var directions=new Vector3dc[options.size()];
        for(int i=0;i<options.size();i++){var p=options.get(i).path;directions[i]=PathGeometry.tangent(p::at,p.a==exitId?0:1).mul(p.a==exitId?1:-1);}
        int pick=BranchSelect.choose(incoming,previous.up,directions,policy==null?BranchSelect.Steer.NONE:policy.steer());
        if(!RailwaysCompat.navigable(options.get(pick).edge)){
            var available=new ArrayList<Integer>();for(int i=0;i<options.size();i++)if(RailwaysCompat.navigable(options.get(i).edge))available.add(i);
            if(!available.isEmpty()){var open=new Vector3dc[available.size()];for(int i=0;i<open.length;i++)open[i]=directions[available.get(i)];pick=available.get(BranchSelect.choose(incoming,previous.up,open,policy==null?BranchSelect.Steer.NONE:policy.steer()));}
        }
        Branch chosen=options.get(pick);
        if(policy!=null&&live.network.getConnectionsFrom(exit).size()>2){
            var target=policy.leader();
            if(target!=null&&!key(target.path).equals(key(live))){var followed=followLeader(options,target);if(followed!=null)chosen=followed;}
            int[] ids=options.stream().mapToInt(c->c.node.getNetId()).toArray();
            int selected=policy.choose(live,exitId,enterId,ids,chosen.node.getNetId(),live.at(endT));
            chosen=null;for(var c:options)if(c.node.getNetId()==selected){chosen=c;break;}
            if(chosen==null)return null;
        }else if(policy==null&&leader!=null){var followed=followLeader(options,leader);if(followed!=null)chosen=followed;}
        if(policy!=null&&!policy.mayPass(live,exitId,enterId,chosen.node.getNetId()))return null;
        if(policy==null&&!RailwaysCompat.navigable(chosen.edge))return null;
        double hint=chosen.path.a==exit.getNetId()?0:1;
        var sample=project(chosen.path,point,heading,hint);
        if(sample==null||sample.position.distanceSquared(point)>radius*radius)return null;
        if(!level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))return null;
        if(policy!=null&&live.network.getConnectionsFrom(exit).size()>2){
            int[] ids=options.stream().mapToInt(c->c.node.getNetId()).toArray();
            if(!policy.commit(live,exitId,enterId,ids,chosen.node.getNetId(),live.at(endT)))return null;
        }
        return sample;
    }
    private record Branch(TrackNode node,TrackEdge edge,Path path){}
    /** Read ahead without committing route memory or moving a body. */
    static double switchDistance(ServerLevel level,Sample sample,Vector3dc heading,boolean reverse,RoutePolicy policy,double horizon,double clearance){
        if(RailwaysCompat.bridge==null||sample==null)return Double.POSITIVE_INFINITY;
        Path path=sample.path;boolean inc=BranchSelect.exitsIncreasing(PathGeometry.tangent(path::at,sample.t),heading,reverse);
        double offset=sample.t*path.edge.getLength(),distance=(inc?1-sample.t:sample.t)*path.edge.getLength();var idx=INDEX.get(level);
        for(int depth=0;depth<128;depth++){
            double start=distance-(inc?path.edge.getLength()-offset:offset),bufferDistance=Double.POSITIVE_INFINITY;
            for(double buffer:RailwaysCompat.buffers(path)){
                double along=inc?buffer-offset:offset-buffer;
                if(along>=-.05)bufferDistance=Math.min(bufferDistance,Math.max(0,start+along-clearance));
            }
            if(Double.isFinite(bufferDistance))return bufferDistance;
            if(distance>horizon)return Double.POSITIVE_INFINITY;
            int node=inc?path.b:path.a,from=inc?path.a:path.b;
            var exit=path.network.getNode(node);var enter=path.network.getNode(from);var incoming=path.network.getConnectionsFrom(enter).get(exit);
            var options=new ArrayList<Branch>();
            for(var entry:path.network.getConnectionsFrom(exit).entrySet()){
                if(entry.getKey()==enter||entry.getValue().isInterDimensional()||!RailwaysCompat.geometry(incoming,entry.getValue()))continue;
                var next=idx.paths.get(key(path.graph,node,entry.getKey().getNetId()));if(next!=null&&next.supports(policy.gauge()))options.add(new Branch(entry.getKey(),entry.getValue(),next));
            }
            if(options.isEmpty())return Double.POSITIVE_INFINITY;
            options.sort(Comparator.comparingInt(b->b.node.getNetId()));
            if(options.size()>1||path.network.getConnectionsFrom(exit).size()>2){
                var available=options.stream().filter(b->RailwaysCompat.navigable(b.edge)).toList();int proposed=-1;
                if(!available.isEmpty()){
                    var directions=new Vector3dc[available.size()];for(int i=0;i<directions.length;i++){var b=available.get(i);directions[i]=PathGeometry.tangent(b.path::at,b.path.a==node?0:1).mul(b.path.a==node?1:-1);}
                    var tangent=PathGeometry.tangent(path::at,inc?1:0).mul(inc?1:-1);
                    proposed=available.get(BranchSelect.choose(tangent,sample.up,directions,policy.steer())).node.getNetId();
                }
                int selected=policy.choose(path,node,from,options.stream().mapToInt(b->b.node.getNetId()).toArray(),proposed,path.at(inc?1:0));
                if(selected<0||!policy.mayPass(path,node,from,selected))return Math.max(0,distance-.4);
                Branch chosen=null;for(var option:options)if(option.node.getNetId()==selected){chosen=option;break;}
                if(chosen==null)return Math.max(0,distance-.4);
                options.clear();options.add(chosen);
            }
            var next=options.getFirst();inc=next.path.a==node;path=next.path;offset=inc?0:path.edge.getLength();distance+=path.edge.getLength();
        }
        return Double.POSITIVE_INFINITY;
    }
    private static Branch steerBranch(List<Branch> options,TrackEdge current,Vector3dc up,Vector3dc heading){
        var exitDir=vec(current.getDirection(false));
        var entries=new Vector3d[options.size()];
        for(int i=0;i<options.size();i++)entries[i]=vec(options.get(i).edge.getDirection(true));
        int pick=BranchSelect.steer(BranchSelect.fromHeading(exitDir,up,heading),exitDir,up,entries);
        return options.get(Math.max(0,pick));
    }
    /** Prefer the outgoing edge that reaches the leader's current nodes (Create {@code follow}). */
    private static Branch followLeader(List<Branch> options,Sample leader){
        int la=leader.path.a,lb=leader.path.b;
        for(var c:options)if(c.path.equals(leader.path)||c.node.getNetId()==la||c.node.getNetId()==lb)return c;
        record Step(TrackNode node,TrackEdge via){}
        var frontiers=new ArrayList<ArrayDeque<Step>>();
        var visited=new ArrayList<HashSet<TrackEdge>>();
        for(var c:options){
            var q=new ArrayDeque<Step>();q.add(new Step(c.node,c.edge));frontiers.add(q);
            var seen=new HashSet<TrackEdge>();seen.add(c.edge);visited.add(seen);
        }
        for(int depth=0;depth<256;depth++)for(int j=0;j<options.size();j++){
            var frontier=frontiers.get(j);if(frontier.isEmpty())continue;
            var step=frontier.removeFirst();
            if(step.node.getNetId()==la||step.node.getNetId()==lb)return options.get(j);
            for(var next:options.get(j).path.network.getConnectionsFrom(step.node).entrySet()){
                if(!visited.get(j).add(next.getValue())||!step.via.canTravelTo(next.getValue()))continue;
                frontier.add(new Step(next.getKey(),next.getValue()));
            }
        }
        return null;
    }
    private static Vector3d vec(Vec3 v){return new Vector3d(v.x,v.y,v.z);}
    public static Sample nearest(ServerLevel level,Vector3dc point,Vector3dc heading,Path previous,double radius){return nearest(level,point,heading,previous,radius,RailGauge.STANDARD);}
    static Sample nearest(ServerLevel level,Vector3dc point,Vector3dc heading,Path previous,double radius,RailGauge gauge){
        var idx=INDEX.get(level);if(idx==null)return null;var candidates=idx.cells.getOrDefault(cell(point.x(),point.z()),Set.of());
        Sample best=null;double score=Double.POSITIVE_INFINITY;
        Path live=previous==null?null:idx.paths.get(key(previous));
        for(var p:candidates){
            if(!p.supports(gauge))continue;
            if(live!=null&&!p.equals(live)&&!p.adjacent(live))continue;
            var sample=project(p,point,heading,null);if(sample==null)continue;double d=sample.position.distanceSquared(point);double align=sample.tangent.dot(heading);
            if(d>radius*radius||align<.5||!level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))continue;
            double rank=d+.02*(1-align);if(live!=null&&key(p).equals(key(live)))rank-=.00002;
            if(rank<score){score=rank;best=sample;}
        }
        return best;
    }
    /** Interactive actions must not use the half-second background index throttle. */
    public static void refreshNow(ServerLevel level){INDEX.computeIfAbsent(level,l->new Index()).next=0;refresh(level);}
    /** Server-side rail picking also hits Bezier track drawn through air, without an ITrackBlock hit. */
    public static Sample pick(net.minecraft.server.level.ServerPlayer player){return pick(player,RailGauge.STANDARD);}
    static Sample pick(net.minecraft.server.level.ServerPlayer player,RailGauge gauge){
        var level=player.serverLevel();refreshNow(level);
        var eye=player.getEyePosition();var look=player.getLookAngle();double reach=player.blockInteractionRange();
        var end=eye.add(look.scale(reach));
        var block=level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=block.getLocation().add(look.scale(.05));
        var a=new Vector3d(eye.x,eye.y,eye.z);var b=new Vector3d(end.x,end.y,end.z);var heading=new Vector3d(look.x,look.y,look.z);
        var idx=INDEX.get(level);var candidates=new HashSet<Path>();
        for(int i=0;i<=16;i++){var q=new Vector3d(a).lerp(b,i/16.0);candidates.addAll(idx.cells.getOrDefault(cell(q.x,q.z),Set.of()));}
        Sample best=null;double score=Double.POSITIVE_INFINITY;
        for(var path:candidates){
            if(!path.supports(gauge))continue;
            int n=Math.clamp((int)Math.ceil(path.edge.getLength()/.25),1,16384);
            var prev=at(path,0,heading);
            for(int i=1;i<=n;i++){
                var next=at(path,(double)i/n,heading);
                for(double offset:new double[]{0,-.875,.875}){
                    var prevTop=new Vector3d(prev.position).fma(BogieGeometry.RAIL_TOP,prev.up).fma(offset,new Vector3d(prev.tangent).cross(prev.up));
                    var top=new Vector3d(next.position).fma(BogieGeometry.RAIL_TOP,next.up).fma(offset,new Vector3d(next.tangent).cross(next.up));
                    var hit=RailPlacement.raySegment(a,b,prevTop,top);double tolerance=offset==0?.55:.30;
                    double rank=hit.distanceSquared()+hit.rayFraction()*1e-5;
                    if(hit.distanceSquared()<=tolerance*tolerance&&rank<score){
                        var selectedTop=new Vector3d(prevTop).lerp(top,hit.segmentFraction());
                        double depth=new Vector3d(selectedTop).sub(a).dot(heading);if(depth<0||depth>a.distance(b)+.02)continue;
                        double t=(i-1+hit.segmentFraction())/n;var chosen=at(path,t,heading);
                        if(level.hasChunkAt(BlockPos.containing(chosen.position.x,chosen.position.y,chosen.position.z))){best=chosen;score=rank;}
                    }
                }
                prev=next;
            }
        }
        return best;
    }
    private static Sample at(Path path,double t,Vector3dc heading){
        var tangent=PathGeometry.tangent(path::at,t);if(tangent.dot(heading)<0)tangent.negate();
        var normal=path.edge.getNormal(path.network,t);return new Sample(path,t,path.at(t),tangent,new Vector3d(normal.x,normal.y,normal.z).normalize());
    }
    private static final class ArcTable {
        final double[] distance;
        ArcTable(Path p){int n=Math.clamp((int)Math.ceil(p.edge.getLength()/.2),1,16384);distance=new double[n+1];var prev=p.at(0);for(int i=1;i<=n;i++){var next=p.at((double)i/n);distance[i]=distance[i-1]+prev.distance(next);prev=next;}}
        double length(){return distance[distance.length-1];}
        double distance(double t){double index=Math.clamp(t,0,1)*(distance.length-1);int i=Math.min(distance.length-2,(int)index);return distance[i]+(distance[i+1]-distance[i])*(index-i);}
        double parameter(double d){d=Math.clamp(d,0,length());int lo=0,hi=distance.length-1;while(hi-lo>1){int mid=(lo+hi)/2;if(distance[mid]<d)lo=mid;else hi=mid;}double span=distance[hi]-distance[lo];return (lo+(span>1e-12?(d-distance[lo])/span:0))/(distance.length-1);}
    }
    /** One connected, deterministic route, sampled by arc length for placing and fitting bodies. */
    public static final class PlacementRoute implements RailPlacement.Route {
        private record Span(Path path,boolean increasing,double start,ArcTable arc){double end(){return start+arc.length();}}
        final ServerLevel level;final Set<RailGauge> gauges;final List<Span> spans=new ArrayList<>();final Map<Path,ArcTable> tables=new HashMap<>();
        PlacementRoute(ServerLevel level,Sample anchor,double extent,Set<RailGauge> gauges){
            this.level=level;this.gauges=Set.copyOf(gauges);if(gauges.stream().anyMatch(g->!anchor.path.supports(g)))throw new IllegalArgumentException("message.guidedrailways.rail.gauge");var arc=table(anchor.path);boolean increasing=PathGeometry.tangent(anchor.path::at,anchor.t).dot(anchor.tangent)>=0;
            spans.add(new Span(anchor.path,increasing,-(increasing?arc.distance(anchor.t):arc.length()-arc.distance(anchor.t)),arc));
            for(int i=0;i<256&&spans.get(spans.size()-1).end()<extent;i++)if(!extend(true))break;
            for(int i=0;i<256&&spans.get(0).start> -extent;i++)if(!extend(false))break;
        }
        private ArcTable table(Path p){return tables.computeIfAbsent(p,ArcTable::new);}
        private boolean extend(boolean positive){
            var span=positive?spans.get(spans.size()-1):spans.get(0);boolean increasing=positive?span.increasing:!span.increasing;
            int exitId=increasing?span.path.b:span.path.a,entryId=increasing?span.path.a:span.path.b;
            var exit=span.path.network.getNode(exitId);var entry=span.path.network.getNode(entryId);if(exit==null||entry==null)return false;
            var incoming=span.path.network.getConnectionsFrom(entry).get(exit);if(incoming==null)return false;
            var wanted=PathGeometry.tangent(span.path::at,increasing?1:0).mul(increasing?1:-1);
            Path best=null;double alignment=-2;int bestId=Integer.MAX_VALUE;var idx=INDEX.get(level);
            for(var e:span.path.network.getConnectionsFrom(exit).entrySet()){
                if(e.getKey()==entry||e.getValue().isInterDimensional()||!incoming.canTravelTo(e.getValue()))continue;
                var p=idx.paths.get(key(span.path.graph,exitId,e.getKey().getNetId()));if(p==null||gauges.stream().anyMatch(g->!p.supports(g)))continue;
                var dir=PathGeometry.tangent(p::at,p.a==exitId?0:1).mul(p.a==exitId?1:-1);double dot=dir.dot(wanted);
                if(dot>alignment+1e-9||(Math.abs(dot-alignment)<1e-9&&e.getKey().getNetId()<bestId)){best=p;alignment=dot;bestId=e.getKey().getNetId();}
            }
            if(best==null||alignment<.25)return false;
            var arc=table(best);if(arc.length()<1e-6)return false;
            boolean nextIncreasing=positive?best.a==exitId:best.b==exitId;
            var next=new Span(best,nextIncreasing,positive?span.end():span.start-arc.length(),arc);
            if(positive)spans.add(next);else spans.add(0,next);return true;
        }
        public Sample sample(double d){
            for(var span:spans)if(d>=span.start-1e-6&&d<=span.end()+1e-6){
                double local=Math.clamp(d-span.start,0,span.arc.length());double t=span.arc.parameter(span.increasing?local:span.arc.length()-local);
                var forward=PathGeometry.tangent(span.path::at,t).mul(span.increasing?1:-1);var result=TrackNetwork.at(span.path,t,forward);
                return level.hasChunkAt(BlockPos.containing(result.position.x,result.position.y,result.position.z))?result:null;
            }
            return null;
        }
        @Override public RailPlacement.Point at(double d){var s=sample(d);return s==null?null:new RailPlacement.Point(s.position,s.tangent,s.up);}
    }
    public static PlacementRoute placementRoute(ServerLevel level,Sample anchor,double extent){return new PlacementRoute(level,anchor,extent,Set.of(RailGauge.STANDARD));}
    static PlacementRoute placementRoute(ServerLevel level,Sample anchor,double extent,Set<RailGauge> gauges){return new PlacementRoute(level,anchor,extent,gauges);}
    static void unload(ServerLevel level){INDEX.remove(level);}
}
