package com.guidedrailways;
import java.util.*;
/** Pure topology policy; only nearby, attached bogies in the same consist may overlap. */
final class BogieContactPolicy {
    record Link(UUID a,UUID b){}
    record Body(UUID id,UUID carrier,double x,double y,double z,boolean detached){}
    record Pair(UUID a,UUID b){
        static Pair of(UUID a,UUID b){return a.compareTo(b)<0?new Pair(a,b):new Pair(b,a);}
    }
    static Set<Pair> exclusions(List<Body> bodies,List<Link> links){
        var graph=new HashMap<UUID,Set<UUID>>();
        for(var l:links){graph.computeIfAbsent(l.a,k->new HashSet<>()).add(l.b);graph.computeIfAbsent(l.b,k->new HashSet<>()).add(l.a);}
        var groups=new HashMap<UUID,UUID>();
        for(var body:bodies){
            if(groups.containsKey(body.carrier))continue;
            var queue=new ArrayDeque<UUID>();queue.add(body.carrier);groups.put(body.carrier,body.carrier);
            while(!queue.isEmpty())for(var n:graph.getOrDefault(queue.remove(),Set.of()))if(!groups.containsKey(n)){groups.put(n,body.carrier);queue.add(n);}
        }
        var result=new HashSet<Pair>();
        for(int i=0;i<bodies.size();i++)for(int j=i+1;j<bodies.size();j++){
            var a=bodies.get(i);var b=bodies.get(j);
            if(a.detached||b.detached||!groups.get(a.carrier).equals(groups.get(b.carrier)))continue;
            double x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;
            if(x*x+y*y+z*z<64)result.add(Pair.of(a.id,b.id));
        }
        return result;
    }
}
