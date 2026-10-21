package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import java.util.*;

/** Query projection of the physical ledger, never a second reservation controller. */
final class NativeSignalState {
  private final PhysicalTrainProxy train;
  private final Set<SignalEdgeGroup> groups = new HashSet<>();
  private boolean ready;

  boolean ready() {
    return ready;
  }

  NativeSignalState(PhysicalTrainProxy train) {
    this.train = train;
  }

  void update(Set<UUID> occupied, Set<UUID> claimed) {
    clear();
    ready = true;
    var registry = Create.RAILWAYS.signalEdgeGroups;
    for (var id : occupied) {
      var group = registry.get(id);
      if (group == null) continue;
      groups.add(group);
      // The physical coverage ledger does not invent a boundary traversal history.
      train.occupiedSignalBlocks.put(id, null);
    }
    for (var id : claimed) if (registry.containsKey(id)) train.reservedSignalBlocks.add(id);
    publish();
  }

  void publish() {
    if (Create.RAILWAYS.trains.get(train.id) != train) return;
    for (var group : groups)
      if (Create.RAILWAYS.signalEdgeGroups.get(group.id) == group) group.trains.add(train);
  }

  void clear() {
    ready = false;
    for (var group : groups) group.trains.remove(train);
    groups.clear();
    train.occupiedSignalBlocks.clear();
    train.reservedSignalBlocks.clear();
  }
}
