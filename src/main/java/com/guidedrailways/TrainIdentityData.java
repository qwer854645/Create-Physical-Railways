package com.guidedrailways;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Identity persists independently of Create's ordinary carriage reconstruction. */
final class TrainIdentityData extends SavedData {
  final TrainIdentityLedger ledger = new TrainIdentityLedger();

  static TrainIdentityData get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new Factory<>(TrainIdentityData::new, TrainIdentityData::load, null),
            "guidedrailways_trains");
  }

  static TrainIdentityData load(CompoundTag root, HolderLookup.Provider registries) {
    var data = new TrainIdentityData();
    for (var value : root.getList("Trains", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value;
      if (!tag.hasUUID("Id") || !tag.hasUUID("Anchor")) continue;
      var members = new HashSet<UUID>();
      for (var v : tag.getList("Members", Tag.TAG_COMPOUND)) {
        var member = (CompoundTag) v;
        if (member.hasUUID("Id")) members.add(member.getUUID("Id"));
      }
      if (!members.contains(tag.getUUID("Anchor"))) continue;
      var identity =
          new TrainIdentityLedger.Identity(tag.getUUID("Id"), tag.getUUID("Anchor"), members);
      data.ledger.records.put(identity.id(), identity);
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
    var trains = new ListTag();
    for (var r : ledger.records.values()) {
      var t = new CompoundTag();
      t.putUUID("Id", r.id());
      t.putUUID("Anchor", r.anchor());
      var members = new ListTag();
      for (var member : r.members()) {
        var m = new CompoundTag();
        m.putUUID("Id", member);
        members.add(m);
      }
      t.put("Members", members);
      trains.add(t);
    }
    root.put("Trains", trains);
    root.putInt("Version", 1);
    return root;
  }
}
