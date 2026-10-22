package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class CouplerSavedData extends SavedData {
  final CouplingLinks links = new CouplingLinks();
  private final Map<UUID, BlockPos> owners = new HashMap<>();

  static CouplerSavedData get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(CouplerSavedData::new, CouplerSavedData::load, null),
            "guidedrailways_couplings");
  }

  static CouplerSavedData load(CompoundTag root, HolderLookup.Provider registries) {
    var data = new CouplerSavedData();
    for (var value : root.getList("Links", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value;
      try {
        var link = new CouplingLinks.Link(tag.getUUID("Id"), tag.getUUID("A"), tag.getUUID("B"));
        data.links.add(link);
        if (tag.contains("APosition", Tag.TAG_LONG))
          data.owners.put(link.a(), BlockPos.of(tag.getLong("APosition")));
        if (tag.contains("BPosition", Tag.TAG_LONG))
          data.owners.put(link.b(), BlockPos.of(tag.getLong("BPosition")));
      } catch (RuntimeException error) {
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .warn("Ignoring invalid coupler pair in save", error);
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
    var list = new ListTag();
    for (var link : links.all()) {
      var tag = new CompoundTag();
      tag.putUUID("Id", link.id());
      tag.putUUID("A", link.a());
      tag.putUUID("B", link.b());
      if (owners.containsKey(link.a())) tag.putLong("APosition", owners.get(link.a()).asLong());
      if (owners.containsKey(link.b())) tag.putLong("BPosition", owners.get(link.b()).asLong());
      list.add(tag);
    }
    root.put("Links", list);
    root.putInt("Version", 3);
    return root;
  }

  void add(CouplingLinks.Link link, BlockPos a, BlockPos b) {
    links.add(link);
    owners.put(link.a(), a.immutable());
    owners.put(link.b(), b.immutable());
    setDirty();
  }

  boolean owns(UUID endpoint, BlockPos pos) {
    var owner = owners.get(endpoint);
    return owner == null || owner.equals(pos);
  }

  /** Older pairs acquire their missing location once; unloaded registered owners stay protected. */
  boolean claim(UUID endpoint, BlockPos pos) {
    if (!owns(endpoint, pos)) return false;
    if (links.forEndpoint(endpoint) != null && !owners.containsKey(endpoint)) {
      owners.put(endpoint, pos.immutable());
      setDirty();
    }
    return true;
  }

  /** Only a verified assembly handoff may change an existing pair's endpoint location. */
  boolean move(UUID endpoint, BlockPos source, BlockPos destination) {
    if (links.forEndpoint(endpoint) == null) return true;
    if (!owns(endpoint, source)) return false;
    owners.put(endpoint, destination.immutable());
    setDirty();
    return true;
  }

  CouplingLinks.Link remove(java.util.UUID id) {
    var old = links.remove(id);
    if (old != null) {
      owners.remove(old.a());
      owners.remove(old.b());
      setDirty();
    }
    return old;
  }
}
