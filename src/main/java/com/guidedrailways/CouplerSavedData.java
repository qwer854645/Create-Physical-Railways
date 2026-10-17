package com.guidedrailways;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
final class CouplerSavedData extends SavedData {
    final CouplingLinks links=new CouplingLinks();
    static CouplerSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CouplerSavedData::new,CouplerSavedData::load,null),"guidedrailways_couplings");}
    private static CouplerSavedData load(CompoundTag root,HolderLookup.Provider registries){
        var data=new CouplerSavedData();for(var value:root.getList("Links",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)value;
            try{data.links.add(new CouplingLinks.Link(tag.getUUID("Id"),tag.getUUID("A"),tag.getUUID("B")));}
            catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("Ignoring invalid coupler pair in save",error);}
        }return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        var list=new ListTag();for(var link:links.all()){var tag=new CompoundTag();tag.putUUID("Id",link.id());tag.putUUID("A",link.a());tag.putUUID("B",link.b());list.add(tag);}root.put("Links",list);root.putInt("Version",2);return root;
    }
    void add(CouplingLinks.Link link){links.add(link);setDirty();}
    CouplingLinks.Link remove(java.util.UUID id){var old=links.remove(id);if(old!=null)setDirty();return old;}
}
