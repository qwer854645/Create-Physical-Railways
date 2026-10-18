package com.guidedrailways;
import java.util.*;
/** Pure, persistent-order election over the complete coupled carrier component. */
public final class ConsoleElection {
    public record Console(UUID id,UUID carrier,long order){}
    public record Link(UUID a,UUID b){}
    public static Map<UUID,UUID> elect(Collection<Console> consoles,List<Link> links){
        var adjacency=new HashMap<UUID,Set<UUID>>();
        for(var c:consoles)adjacency.computeIfAbsent(c.carrier,k->new HashSet<>());
        for(var l:links){adjacency.computeIfAbsent(l.a,k->new HashSet<>()).add(l.b);adjacency.computeIfAbsent(l.b,k->new HashSet<>()).add(l.a);}
        var result=new HashMap<UUID,UUID>();var seen=new HashSet<UUID>();
        var order=Comparator.comparingLong(Console::order).thenComparing(Console::id);
        for(var root:adjacency.keySet()){
            if(!seen.add(root))continue;var component=new HashSet<UUID>();component.add(root);var queue=new ArrayDeque<UUID>();queue.add(root);
            while(!queue.isEmpty())for(var id:adjacency.getOrDefault(queue.remove(),Set.of()))if(seen.add(id)){component.add(id);queue.add(id);}
            Console first=null;for(var c:consoles)if(component.contains(c.carrier)&&(first==null||order.compare(c,first)<0))first=c;
            if(first!=null)for(var id:component)result.put(id,first.id);
        }
        return Map.copyOf(result);
    }
}
