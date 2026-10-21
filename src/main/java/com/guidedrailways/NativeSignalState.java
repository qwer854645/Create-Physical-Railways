package com.guidedrailways;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

/** Query projection of the physical ledger, never a second reservation controller. */
final class NativeSignalState {
  private final PhysicalTrainProxy train;
  private final Set<SignalEdgeGroup> groups = new HashSet<>();
  private Map<UUID, SignalEdgeGroup> registry = Map.of();
  private Map<UUID, Train> trains = Map.of();
  private SignalFrame desired = SignalFrame.UNKNOWN, projected, sent;
  private String sentDimension;

  boolean ready() {
    return projected != null;
  }

  NativeSignalState(PhysicalTrainProxy train) {
    this.train = train;
  }

  void update(Set<UUID> occupied, Set<UUID> claimed) {
    registry = Create.RAILWAYS.signalEdgeGroups;
    trains = Create.RAILWAYS.trains;
    boolean known =
        occupied.size() <= SignalFrame.LIMIT
            && claimed.size() <= SignalFrame.LIMIT
            && registry.keySet().containsAll(occupied)
            && registry.keySet().containsAll(claimed);
    Set<UUID> actual = known ? occupied : Set.of();
    Set<UUID> reservations = known ? claimed : Set.of();
    if (desired.known() != known
        || !desired.occupied().equals(actual)
        || !desired.claimed().equals(reservations))
      desired = new SignalFrame(desired.revision() + 1, known, actual, reservations);
    resolve();
    String dimension = train.getPresentDimensions().getFirst().location().toString();
    if (!desired.equals(sent) || !dimension.equals(sentDimension)) {
      PacketDistributor.sendToAllPlayers(new NativeTrainNetwork.Update(packet()));
      sent = desired;
      sentDimension = dimension;
    }
  }

  CompoundTag packet() {
    var tag = new CompoundTag();
    tag.putUUID("Id", train.id);
    tag.putBoolean("SignalOnly", true);
    tag.putString("Dimension", train.getPresentDimensions().getFirst().location().toString());
    tag.put("Signals", desired.write());
    return tag;
  }

  void accept(SignalFrame frame, Map<UUID, SignalEdgeGroup> registry, Map<UUID, Train> trains) {
    if (frame.revision() < desired.revision()) return;
    this.desired = frame;
    retry(registry, trains);
  }

  void retry(Map<UUID, SignalEdgeGroup> registry, Map<UUID, Train> trains) {
    this.registry = registry;
    this.trains = trains;
    resolve();
  }

  private void resolve() {
    boolean available = desired.resolvable(registry.keySet());
    if (projected != null
        && desired.equals(projected)
        && available
        && groups.stream().allMatch(g -> registry.get(g.id) == g)) {
      publish();
      return;
    }
    clearProjection();
    if (!available) return;
    for (var id : desired.occupied()) {
      var group = registry.get(id);
      groups.add(group);
      // Physical coverage has no native boundary traversal history.
      train.occupiedSignalBlocks.put(id, null);
    }
    train.reservedSignalBlocks.addAll(desired.claimed());
    projected = desired;
    publish();
  }

  void publish() {
    if (trains.get(train.id) != train) return;
    for (var group : groups) if (registry.get(group.id) == group) group.trains.add(train);
  }

  private void clearProjection() {
    projected = null;
    for (var group : groups) group.trains.remove(train);
    groups.clear();
    train.occupiedSignalBlocks.clear();
    train.reservedSignalBlocks.clear();
  }

  void clear() {
    clearProjection();
    desired = new SignalFrame(desired.revision() + 1, false, Set.of(), Set.of());
    registry = Map.of();
    trains = Map.of();
  }

  void resetClient() {
    clearProjection();
    desired = SignalFrame.UNKNOWN;
    registry = Map.of();
    trains = Map.of();
  }
}
