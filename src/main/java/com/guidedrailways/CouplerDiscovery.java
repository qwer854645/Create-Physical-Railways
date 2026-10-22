package com.guidedrailways;

import dev.ryanhcode.sable.api.sublevel.*;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Loaded-body events are primary; unresolved saved links get an incremental fallback audit. */
final class CouplerDiscovery {
  private static final int WORK_PER_TICK = 128;

  private static final class Scan {
    final Iterator<ChunkPos> chunks;
    Iterator<BlockPos> positions = Collections.emptyIterator();
    ChunkPos chunk;

    Scan(SubLevel body) {
      chunks =
          body.getPlot().getLoadedChunks().stream()
              .map(holder -> holder.getChunk().getPos())
              .toList()
              .iterator();
    }
  }

  private static final class State {
    final RecoveryQueue<UUID> pending = new RecoveryQueue<>();
    final Map<UUID, Scan> scans = new HashMap<>();
    long nextAudit;
  }

  private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

  static void observe(SubLevelContainer container) {
    container.addObserver(
        new SubLevelObserver() {
          @Override
          public void onSubLevelAdded(SubLevel body) {
            if (body.getLevel() instanceof ServerLevel level)
              STATES.computeIfAbsent(level, l -> new State()).pending.add(body.getUniqueId());
          }

          @Override
          public void onSubLevelRemoved(SubLevel body, SubLevelRemovalReason reason) {
            if (body.getLevel() instanceof ServerLevel level) {
              var state = STATES.get(level);
              if (state != null) {
                state.pending.remove(body.getUniqueId());
                state.scans.remove(body.getUniqueId());
              }
            }
          }
        });
  }

  static void tick(ServerLevel level, boolean unresolved) {
    var state = STATES.computeIfAbsent(level, l -> new State());
    if (!unresolved) {
      state.pending.clear();
      state.scans.clear();
      return;
    }
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return;
    if (level.getGameTime() >= state.nextAudit) {
      state.nextAudit = level.getGameTime() + 200;
      for (var body : container.getAllSubLevels())
        if (!body.isRemoved()) state.pending.add(body.getUniqueId());
    }
    state.pending.drain(
        WORK_PER_TICK,
        id -> {
          var body = container.getSubLevel(id);
          if (body == null || body.isRemoved()) {
            state.scans.remove(id);
            return true;
          }
          var scan = state.scans.computeIfAbsent(id, key -> new Scan(body));
          if (!scan.positions.hasNext()) {
            if (!scan.chunks.hasNext()) {
              state.scans.remove(id);
              return true;
            }
            scan.chunk = scan.chunks.next();
            var holder = container.getChunkHolder(scan.chunk);
            if (holder != null)
              scan.positions = List.copyOf(holder.getChunk().getBlockEntitiesPos()).iterator();
            return false;
          }
          var pos = scan.positions.next();
          var holder = container.getChunkHolder(scan.chunk);
          if (holder == null) return false;
          var chunk = holder.getChunk();
          var block = chunk.getBlockState(pos);
          if (block.is(Content.COUPLER.get()) || RailwayHardware.coupler(block)) {
            var endpoint = CouplerEndpoint.get(chunk.getBlockEntity(pos));
            if (endpoint != null && !endpoint.isRemoved())
              CouplerConnections.register(level, endpoint);
          }
          return false;
        });
  }

  static void unload(ServerLevel level) {
    STATES.remove(level);
  }

  private CouplerDiscovery() {}
}
