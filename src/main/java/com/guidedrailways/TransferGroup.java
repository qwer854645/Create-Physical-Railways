package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Walk saved couplings and verified mount ownership, independent of the transient live joint cache. */
final class TransferGroup {
  static LinkedHashMap<UUID, ServerSubLevel> collect(ServerLevel level, ServerSubLevel root) {
    var container = SubLevelContainer.getContainer(level);
    var data = CouplerSavedData.get(level);
    var bodies = new LinkedHashMap<UUID, ServerSubLevel>();
    int entities = 0;
    var queue = new ArrayDeque<ServerSubLevel>(); queue.add(root);
    while (!queue.isEmpty()) {
      var body = queue.remove();
      if (body.isRemoved() || body.getLevel() != level) throw incomplete();
      if (bodies.putIfAbsent(body.getUniqueId(), body) != null) continue;
      if (bodies.size() > PhysicalTransfer.MAX_BODIES) throw new IllegalArgumentException("message.guidedrailways.transfer.limit");
      var box = body.boundingBox().toMojang(); double volume = box.getXsize() * box.getYsize() * box.getZsize();
      if (!Double.isFinite(volume) || volume <= 0 || volume > PhysicalTransfer.MAX_VOLUME)
        throw new IllegalArgumentException("message.guidedrailways.transfer.limit");
      if (body.getPlot().getLoadedChunks().isEmpty()) throw incomplete();
      for (var be : entities(body)) {
        if (++entities > PhysicalTransfer.MAX_BLOCKS) throw new IllegalArgumentException("message.guidedrailways.transfer.limit");
        if (be instanceof MountEntity mount) {
          if (mount.bogieId == null || mount.handoff != null
              || !(container.getSubLevel(mount.bogieId) instanceof ServerSubLevel lower)
              || !level.getBlockState(lower.getPlot().getCenterBlock()).is(Content.BOGIE.get())
              || !BogieMountBinding.matches(lower.getUserDataTag(), VehiclePhysics.mountLocation(level, mount.getBlockPos())))
            throw incomplete();
          queue.add(lower);
        }
        var endpoint = CouplerEndpoint.get(be);
        if (endpoint == null) continue;
        if (endpoint.handoff != null || !data.owns(endpoint.endpointId, be.getBlockPos())) throw incomplete();
        var link = data.links.forEndpoint(endpoint.endpointId);
        if (link == null) continue;
        UUID peerId = link.a().equals(endpoint.endpointId) ? link.b() : link.a();
        var pos = data.position(peerId);
        if (pos == null || !level.hasChunkAt(pos)) throw incomplete();
        var peer = level.getBlockEntity(pos);
        var peerEndpoint = peer == null ? null : CouplerEndpoint.get(peer);
        if (peerEndpoint == null || !peerId.equals(peerEndpoint.endpointId)
            || !(SableCompanion.INSTANCE.getContaining(level, pos) instanceof ServerSubLevel other)) throw incomplete();
        queue.add(other);
      }
    }
    return bodies;
  }

  static List<BlockEntity> entities(ServerSubLevel body) {
    var result = new ArrayList<BlockEntity>();
    for (var holder : body.getPlot().getLoadedChunks())
      for (var pos : List.copyOf(holder.getChunk().getBlockEntitiesPos())) {
        var be = holder.getChunk().getBlockEntity(pos);
        if (be == null || be.isRemoved()) throw incomplete();
        result.add(be);
      }
    return result;
  }

  private static IllegalArgumentException incomplete() {
    return new IllegalArgumentException("message.guidedrailways.transfer.incomplete");
  }
  private TransferGroup() {}
}
