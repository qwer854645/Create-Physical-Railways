package com.guidedrailways;
import java.util.*;
/** Per-consist junction passages. Every axle consumes the same passage, including reverse retracing. */
public final class RouteMemory {
    public record Axle(UUID bogie,int end){}
    public record Junction(UUID graph,int node){}
    private static final class Passage {
        final Junction node;final int from,to;final Map<Axle,Boolean> after=new HashMap<>();
        Passage(Junction node,int from,int to,Set<Axle> members){this.node=node;this.from=from;this.to=to;for(var a:members)after.put(a,false);}
        boolean complete(){return after.values().stream().allMatch(Boolean::booleanValue);}
    }
    private record Repeat(Junction node,int from,int to){}
    private final Set<Axle> members;private final List<Passage> history=new ArrayList<>();private final Map<Axle,Repeat> repeat=new HashMap<>();
    public RouteMemory(Set<Axle> members){this.members=Set.copyOf(members);}
    public Set<Axle> members(){return members;}
    public record Active(Junction node,int from,int to){}
    public List<Active> active(){return history.stream().filter(p->!p.complete()||repeat.values().stream().anyMatch(r->r.node.equals(p.node))).map(p->new Active(p.node,p.from,p.to)).toList();}
    public RouteMemory copyFor(Set<Axle> next){
        var copy=new RouteMemory(next);
        for(var old:history){var p=new Passage(old.node,old.from,old.to,next);for(var a:next)p.after.put(a,old.after.getOrDefault(a,old.complete()));copy.history.add(p);}
        for(var a:next)if(repeat.containsKey(a))copy.repeat.put(a,repeat.get(a));return copy;
    }
    public void clearRepeat(Axle axle){repeat.remove(axle);}
    public int choose(Axle axle,Junction node,int from,int[] exits,int proposed){
        return select(axle,node,from,exits,proposed,true,false);
    }
    public int preview(Axle axle,Junction node,int from,int[] exits,int proposed,boolean planned){
        return select(axle,node,from,exits,proposed,false,planned);
    }
    public int commit(Axle axle,Junction node,int from,int[] exits,int proposed,boolean planned){
        return select(axle,node,from,exits,proposed,true,planned);
    }
    private int select(Axle axle,Junction node,int from,int[] exits,int proposed,boolean commit,boolean planned){
        if(!members.contains(axle))throw new IllegalArgumentException("unknown axle");
        var last=repeat.get(axle);
        if(last!=null&&last.node.equals(node)&&last.from==from)return contains(exits,last.to)?last.to:-1;
        Passage choice=null;boolean forward=true;
        for(var p:history)if(p.node.equals(node)&&p.from==from&&!p.after.getOrDefault(axle,true)){choice=p;break;}
        if(choice==null)for(int i=history.size()-1;i>=0;i--){var p=history.get(i);if(p.node.equals(node)&&p.to==from&&p.after.getOrDefault(axle,false)){choice=p;forward=false;break;}}
        // A fully cleared historical passage is not an active train-spanning lock.
        // Automatic navigation may make a new trip instead of retracing that old visit.
        if(choice!=null&&!forward&&choice.complete()&&planned&&proposed!=choice.from)choice=null;
        if(choice!=null){int out=forward?choice.to:choice.from;if(!contains(exits,out))return -1;
            if(commit){choice.after.put(axle,forward);if(!forward&&choice.after.values().stream().noneMatch(Boolean::booleanValue))history.remove(choice);repeat.put(axle,new Repeat(node,from,out));}
            return out;}
        if(!contains(exits,proposed))return -1;
        if(!commit)return history.size()<4096||history.stream().anyMatch(Passage::complete)?proposed:-1;
        if(history.size()>=512){for(var it=history.iterator();it.hasNext()&&history.size()>=512;)if(it.next().complete())it.remove();}
        // Preserve incomplete passages rather than letting a long consist split between branches.
        if(history.size()>=4096)return -1;
        var p=new Passage(node,from,proposed,members);p.after.put(axle,true);history.add(p);repeat.put(axle,new Repeat(node,from,proposed));return proposed;
    }
    private static boolean contains(int[] values,int needle){for(int v:values)if(v==needle)return true;return false;}
}
