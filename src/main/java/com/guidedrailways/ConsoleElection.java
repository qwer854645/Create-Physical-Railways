package com.guidedrailways;
import java.util.*;
/** Pure, persistent-order election over the complete coupled carrier component. */
public final class ConsoleElection {
    public record Console(UUID id,UUID carrier,long order){}
    public record Link(UUID a,UUID b){}
    public static Map<UUID,UUID> elect(Collection<Console> consoles,List<Link> links){
        var bodies=consoles.stream().map(Console::carrier).toList();
        var edges=links.stream().map(l->new ConsistGraph.Link(l.a(),l.b())).toList();
        return electGroups(consoles,ConsistGraph.components(bodies,edges));
    }
    public static Map<UUID,UUID> electGroups(Collection<Console> consoles,Collection<Set<UUID>> components){
        var result=new HashMap<UUID,UUID>();
        var order=Comparator.comparingLong(Console::order).thenComparing(Console::id);
        for(var component:components){
            Console first=null;for(var c:consoles)if(component.contains(c.carrier)&&(first==null||order.compare(c,first)<0))first=c;
            if(first!=null)for(var id:component)result.put(id,first.id);
        }
        return Map.copyOf(result);
    }
}
