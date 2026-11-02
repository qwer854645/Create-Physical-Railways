package com.guidedrailways;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Coupling ownership is installed before block entities load, so clone protection retains endpoint IDs. */
final class TransferLinks {
  private record Pair(CouplingLinks.Link link, BlockPos a, BlockPos b, BlockPos nextA, BlockPos nextB) {}
  private final ServerLevel source, target;
  private final List<Pair> pairs = new ArrayList<>();

  TransferLinks(ServerLevel source, ServerLevel target, Collection<ServerSubLevel> bodies, Collection<TransferPlots.Move> moves) {
    this.source = source; this.target = target;
    var endpoints = new HashMap<UUID, BlockPos>();
    for (var body : bodies) for (var be : TransferGroup.entities(body)) {
      var endpoint = CouplerEndpoint.get(be);
      if (endpoint != null && endpoints.put(endpoint.endpointId, be.getBlockPos()) != null) throw incomplete();
    }
    var before = CouplerSavedData.get(source); var after = CouplerSavedData.get(target);
    for (var link : before.links.all()) {
      if (!endpoints.containsKey(link.a()) && !endpoints.containsKey(link.b())) continue;
      var a = endpoints.get(link.a()); var b = endpoints.get(link.b());
      if (a == null || b == null || !before.owns(link.a(), a) || !before.owns(link.b(), b)
          || after.links.get(link.id()) != null || after.links.forEndpoint(link.a()) != null
          || after.links.forEndpoint(link.b()) != null) throw incomplete();
      pairs.add(new Pair(link, a, b, TransferPlots.point(moves, a), TransferPlots.point(moves, b)));
    }
  }

  void move(boolean forward) {
    var fromLevel = forward ? source : target; var toLevel = forward ? target : source;
    var from = CouplerSavedData.get(fromLevel); var to = CouplerSavedData.get(toLevel);
    for (var pair : pairs) {
      CouplerConnections.suspend(fromLevel, pair.link.a()); CouplerConnections.suspend(fromLevel, pair.link.b());
      var existing = to.links.get(pair.link.id());
      var a = forward ? pair.nextA : pair.a; var b = forward ? pair.nextB : pair.b;
      if (existing != null && (!existing.equals(pair.link) || !to.owns(pair.link.a(), a) || !to.owns(pair.link.b(), b)))
        throw incomplete();
      if (existing == null) to.add(pair.link, a, b);
      if (from.links.get(pair.link.id()) != null) from.remove(pair.link.id());
    }
  }

  List<CompoundTag> snapshot() {
    return pairs.stream().map(pair -> {
      var tag = new CompoundTag(); tag.putUUID("Id", pair.link.id());
      tag.putUUID("A", pair.link.a()); tag.putUUID("B", pair.link.b());
      tag.putLong("AOld", pair.a.asLong()); tag.putLong("BOld", pair.b.asLong());
      tag.putLong("ANew", pair.nextA.asLong()); tag.putLong("BNew", pair.nextB.asLong()); return tag;
    }).toList();
  }

  private static IllegalArgumentException incomplete() { return new IllegalArgumentException("message.guidedrailways.transfer.incomplete"); }
}
