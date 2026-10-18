package com.guidedrailways;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Optional CAAL cargo integration. Its route recorder and flight controller are untouched. */
public final class LogisticsCargoBridge {
    private LogisticsCargoBridge(){}
    public static List<?> expand(Level level,List<?> entries){
        if(!(level instanceof ServerLevel server)||entries.isEmpty())return entries;
        var result=new LinkedHashMap<BlockPos,Object>();var groups=new HashSet<UUID>();
        try{
            for(var entry:entries)result.put((BlockPos)CargoAdapters.call(entry,"pos"),entry);
            for(var entry:entries){
                var pos=(BlockPos)CargoAdapters.call(entry,"pos");var root=CargoStorage.trainBody(server,pos);if(root==null)continue;
                var snapshot=CargoStorage.snapshot(server,root);if(!snapshot.complete())continue;
                if(!Collections.disjoint(groups,snapshot.bodies()))continue;groups.addAll(snapshot.bodies());
                boolean items=(boolean)CargoAdapters.call(entry,"itemStorage"),fluids=(boolean)CargoAdapters.call(entry,"fluidStorage");
                for(var other:entries){
                    var otherBody=CargoStorage.trainBody(server,(BlockPos)CargoAdapters.call(other,"pos"));
                    if(otherBody!=null&&snapshot.bodies().contains(otherBody.getUniqueId())){
                        items|=(boolean)CargoAdapters.call(other,"itemStorage");fluids|=(boolean)CargoAdapters.call(other,"fluidStorage");
                    }
                }
                var constructor=entry.getClass().getConstructor(BlockPos.class,boolean.class,boolean.class);
                for(var location:snapshot.locations()){
                    // CAAL includes the linked dock pair in its own transfer-buffer stage.
                    if(location.dock())continue;
                    boolean i=items&&location.items(),f=fluids&&location.fluids();
                    if(i||f)result.put(location.pos(),constructor.newInstance(location.pos(),i,f));
                }
            }
            return new ArrayList<>(result.values());
        }catch(ReflectiveOperationException|RuntimeException error){CargoAdapters.warn(entries.getFirst(),error);return entries;}
    }
    public static boolean incomplete(Level level,List<?> entries){
        if(!(level instanceof ServerLevel server))return false;
        try{for(var entry:entries){var body=CargoStorage.trainBody(server,(BlockPos)CargoAdapters.call(entry,"pos"));if(body!=null&&!CargoStorage.snapshot(server,body).complete())return true;}}
        catch(ReflectiveOperationException error){CargoAdapters.warn(entries.getFirst(),error);return true;}
        return false;
    }
    public static Object emptySnapshot(Class<?> type){try{return type.getMethod("empty").invoke(null);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    public static Object stamp(BlockEntity connector,Object contents){
        if(!(connector.getLevel() instanceof ServerLevel level))return contents;
        CargoAdapters.bindConnector(connector);
        try{
            if(!(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,connector.getBlockPos()) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body))return contents;
            var clock=CargoTransfers.clock(level,body.getUniqueId());
            var itemState=new ArrayList<Object>((List<?>)CargoAdapters.call(contents,"items"));
            itemState.add("guidedrailways:exchange="+clock.version());
            return contents.getClass().getConstructor(List.class,List.class,List.class,List.class,int.class,long.class).newInstance(
                itemState,CargoAdapters.call(contents,"fluids"),CargoAdapters.call(contents,"itemStacks"),CargoAdapters.call(contents,"fluidStacks"),CargoAdapters.call(contents,"itemCount"),CargoAdapters.call(contents,"fluidAmount"));
        }catch(ReflectiveOperationException error){CargoAdapters.warn(connector,error);return contents;}
    }
}
