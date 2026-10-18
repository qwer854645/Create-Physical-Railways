package com.guidedrailways;
import java.util.*;
/** Authoritative pairing survives endpoint unload; deletion always updates both ends. */
public final class CouplingLinks {
    public record Link(UUID id,UUID a,UUID b){public UUID other(UUID endpoint){return a.equals(endpoint)?b:b.equals(endpoint)?a:null;}}
    private final Map<UUID,Link> links=new HashMap<>();
    private final Map<UUID,UUID> endpoints=new HashMap<>();
    public Collection<Link> all(){return List.copyOf(links.values());}
    public Link forEndpoint(UUID endpoint){return links.get(endpoints.get(endpoint));}
    public Link get(UUID id){return links.get(id);}
    public void add(Link link){
        if(link.id==null||link.a==null||link.b==null||link.a.equals(link.b)||links.containsKey(link.id)||endpoints.containsKey(link.a)||endpoints.containsKey(link.b))throw new IllegalArgumentException("message.guidedrailways.coupler.invalid_link");
        links.put(link.id,link);endpoints.put(link.a,link.id);endpoints.put(link.b,link.id);
    }
    public Link remove(UUID id){var old=links.remove(id);if(old!=null){endpoints.remove(old.a,id);endpoints.remove(old.b,id);}return old;}
}
