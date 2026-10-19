package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persist endpoint locations so an unloaded adapter is not mistaken for an absent station. */
final class RailDockIndex extends SavedData {
    private final Map<BlockPos,UUID> bindings=new HashMap<>();
    static RailDockIndex get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RailDockIndex::new,RailDockIndex::load),"guidedrailways_rail_docks");}
    void bind(BlockPos pos,UUID station){
        var before=station==null?bindings.remove(pos):bindings.put(pos.immutable(),station);
        if(!Objects.equals(before,station))setDirty();
    }
    List<BlockPos> endpoints(UUID station){return bindings.entrySet().stream().filter(e->e.getValue().equals(station)).map(Map.Entry::getKey).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();}
    private static RailDockIndex load(CompoundTag tag,HolderLookup.Provider registries){
        var index=new RailDockIndex();var entries=tag.getList("Bindings",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){var entry=entries.getCompound(i);if(entry.hasUUID("Station"))index.bindings.put(BlockPos.of(entry.getLong("Position")),entry.getUUID("Station"));}
        return index;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();bindings.forEach((pos,id)->{var entry=new CompoundTag();entry.putLong("Position",pos.asLong());entry.putUUID("Station",id);list.add(entry);});tag.put("Bindings",list);return tag;
    }
}
