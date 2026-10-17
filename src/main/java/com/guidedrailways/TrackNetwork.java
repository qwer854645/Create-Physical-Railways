package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import org.joml.*;
import java.util.*;

/** Read-only Create graph adapter. It never creates blocks, rail colliders or train entities. */
public final class TrackNetwork {
    public record Path(UUID graph,int a,int b,TrackGraph network,TrackEdge edge) {
        Vector3d at(double t){var v=edge.getPosition(network,t);return new Vector3d(v.x,v.y,v.z);}
        boolean adjacent(Path p){return graph.equals(p.graph)&&(a==p.a||a==p.b||b==p.a||b==p.b);}
    }
    public record Sample(Path path,double t,Vector3d position,Vector3d tangent,Vector3d up){
        Quaterniond orientation(){var x=new Vector3d(tangent);var y=new Vector3d(up);var z=new Vector3d(x).cross(y).normalize();y.set(z).cross(x).normalize();return new Quaterniond().setFromNormalized(new Matrix3d(x,y,z));}
    }
    private static final class Index {
        long next; final Map<Long,Set<Path>> cells=new HashMap<>();final Map<String,Path> paths=new HashMap<>();
    }
    private static final Map<ServerLevel,Index> INDEX=new WeakHashMap<>();
    private static long cell(double x,double z){return net.minecraft.world.level.ChunkPos.asLong((int)Math.floor(x/16),(int)Math.floor(z/16));}
    private static String key(Path p){return p.graph+":"+p.a+":"+p.b;}
    public static void refresh(ServerLevel level){
        var idx=INDEX.computeIfAbsent(level,l->new Index());if(level.getGameTime()<idx.next)return;idx.next=level.getGameTime()+10;
        idx.cells.clear();idx.paths.clear();
        for(var graph:Create.RAILWAYS.sided(level).trackNetworks.values())for(var loc:graph.getNodes()){
            if(!loc.dimension.equals(level.dimension()))continue;var node=graph.locateNode(loc);if(node==null)continue;
            for(var entry:graph.getConnectionsFrom(node).entrySet()){
                var other=entry.getKey();var edge=entry.getValue();if(node.getNetId()>=other.getNetId()||edge.isInterDimensional())continue;
                var path=new Path(graph.id,node.getNetId(),other.getNetId(),graph,edge);idx.paths.put(key(path),path);
                int n=Math.max(1,(int)Math.ceil(edge.getLength()/4));
                for(int i=0;i<=n;i++){var p=path.at((double)i/n);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)idx.cells.computeIfAbsent(cell(p.x+dx*16,p.z+dz*16),v->new HashSet<>()).add(path);}
            }
        }
    }
    private static Sample project(Path path,Vector3dc query,Vector3dc heading){
        double best=Double.POSITIVE_INFINITY,seed=0;int n=Math.clamp((int)Math.ceil(path.edge.getLength()/2),8,512);
        for(int i=0;i<=n;i++){double t=(double)i/n;double d=path.at(t).distanceSquared(query);if(d<best){best=d;seed=t;}}
        double lo=Math.max(0,seed-1.0/n),hi=Math.min(1,seed+1.0/n);
        for(int i=0;i<18;i++){double a=(2*lo+hi)/3,b=(lo+2*hi)/3;if(path.at(a).distanceSquared(query)<path.at(b).distanceSquared(query))hi=b;else lo=a;}
        double t=(lo+hi)*.5;var p=path.at(t);if(path.at(0).distanceSquared(query)<p.distanceSquared(query)){t=0;p=path.at(0);}if(path.at(1).distanceSquared(query)<p.distanceSquared(query)){t=1;p=path.at(1);}
        var direction=path.edge.getDirectionAt(t);var x=new Vector3d(direction.x,direction.y,direction.z).normalize();
        if(x.dot(heading)<0)x.negate();var normal=path.edge.getNormal(path.network,t);var up=new Vector3d(normal.x,normal.y,normal.z).normalize();
        return new Sample(path,t,p,x,up);
    }
    public static Sample nearest(ServerLevel level,Vector3dc point,Vector3dc heading,Path previous,double radius){
        var idx=INDEX.get(level);if(idx==null)return null;var candidates=idx.cells.getOrDefault(cell(point.x(),point.z()),Set.of());
        Sample best=null;double score=Double.POSITIVE_INFINITY;
        Path live=previous==null?null:idx.paths.get(key(previous));
        for(var p:candidates){
            if(live!=null&&!p.equals(live)&&!p.adjacent(live))continue;
            var sample=project(p,point,heading);double d=sample.position.distanceSquared(point);double align=sample.tangent.dot(heading);
            if(d>radius*radius||align<.5||!level.hasChunkAt(BlockPos.containing(sample.position.x,sample.position.y,sample.position.z)))continue;
            double rank=d+.02*(1-align);if(live!=null&&key(p).equals(key(live)))rank-=.00002;
            if(rank<score){score=rank;best=sample;}
        }
        return best;
    }
    static void unload(ServerLevel level){INDEX.remove(level);}
}
