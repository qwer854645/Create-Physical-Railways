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
    private record PathKey(UUID graph,int a,int b){}
    private static final class Index {
        long next; final Map<Long,Set<Path>> cells=new HashMap<>();final Map<PathKey,Path> paths=new HashMap<>();
    }
    private static final Map<ServerLevel,Index> INDEX=new WeakHashMap<>();
    private static long cell(double x,double z){return net.minecraft.world.level.ChunkPos.asLong((int)Math.floor(x/16),(int)Math.floor(z/16));}
    private static PathKey key(Path p){return new PathKey(p.graph,p.a,p.b);}
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
    public static Sample follow(ServerLevel level,Vector3dc point,Vector3dc heading,Sample previous,double radius){
        PerfCounters.query(level);
        var idx=INDEX.get(level);if(previous!=null&&idx!=null){
            var live=idx.paths.get(key(previous.path));
            if(live!=null){
                var sample=project(live,point,heading,previous.t);
                if(sample!=null&&sample.t>.0001&&sample.t<.9999&&sample.position.distanceSquared(point)<.09
                        &&sample.tangent.dot(heading)>.75&&level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))return sample;
            }
        }
        return nearest(level,point,heading,previous==null?null:previous.path,radius);
    }
    public static Sample nearest(ServerLevel level,Vector3dc point,Vector3dc heading,Path previous,double radius){
        var idx=INDEX.get(level);if(idx==null)return null;var candidates=idx.cells.getOrDefault(cell(point.x(),point.z()),Set.of());
        Sample best=null;double score=Double.POSITIVE_INFINITY;
        Path live=previous==null?null:idx.paths.get(key(previous));
        for(var p:candidates){
            if(live!=null&&!p.equals(live)&&!p.adjacent(live))continue;
            var sample=project(p,point,heading,null);if(sample==null)continue;double d=sample.position.distanceSquared(point);double align=sample.tangent.dot(heading);
            if(d>radius*radius||align<.5||!level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))continue;
            double rank=d+.02*(1-align);if(live!=null&&key(p).equals(key(live)))rank-=.00002;
            if(rank<score){score=rank;best=sample;}
        }
        return best;
    }
    static void unload(ServerLevel level){INDEX.remove(level);}
}
