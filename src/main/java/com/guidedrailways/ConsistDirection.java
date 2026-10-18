package com.guidedrailways;
import java.util.*;
/** Signs are local to each carrier, so bends and opposite-facing bogies do not reverse traction. */
public final class ConsistDirection {
    public record Link(UUID a,UUID b,int outwardA,int outwardB){}
    public static Map<UUID,Integer> orient(UUID root,int direction,List<Link> links){
        var signs=new HashMap<UUID,Integer>();var queue=new ArrayDeque<UUID>();signs.put(root,direction);queue.add(root);
        while(!queue.isEmpty()){
            UUID id=queue.remove();
            for(var link:links){
                boolean first=link.a.equals(id);if(!first&&!link.b.equals(id))continue;
                UUID next=first?link.b:link.a;
                int sign=-signs.get(id)*link.outwardA*link.outwardB;
                if(signs.containsKey(next)){if(signs.get(next)!=sign)return null;}
                else{signs.put(next,sign);queue.add(next);}
            }
        }
        return Map.copyOf(signs);
    }
}
