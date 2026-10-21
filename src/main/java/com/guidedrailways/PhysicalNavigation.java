package com.guidedrailways;

import com.guidedrailways.mixin.NavigationAccessor;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Native navigation queries without native motion, signal scouting or station reservations. */
final class PhysicalNavigation extends Navigation {
  PhysicalNavigation(PhysicalTrainProxy train) {
    super(train);
  }

  void project(CompoundTag tag) {
    destination = null;
    distanceToDestination = distanceStartedAt = distanceToSignal = 0;
    destinationBehindTrain = announceArrival = false;
    waitingForSignal = null;
    ticksWaitingForSignal = 0;
    var path = ((NavigationAccessor) (Object) this).guidedrailways$path();
    path.clear();
    if (train.derailed || train.graph == null || !tag.hasUUID("Destination")) return;
    if (train.carriages.isEmpty()) return;
    for (var carriage : train.carriages) {
      if (!ready(carriage.bogeys.getFirst()) || !ready(carriage.bogeys.getSecond())) return;
    }
    var station = train.graph.getPoint(EdgePointType.STATION, tag.getUUID("Destination"));
    double remaining = tag.getDouble("Remaining"), start = tag.getDouble("StartedAt");
    if (station == null
        || station.assembling
        || !Double.isFinite(remaining)
        || remaining < 0
        || !Double.isFinite(start)
        || start < 0) return;
    var nodes = tag.getIntArray("Path");
    var branches = tag.getIntArray("Branches");
    if (!NativeTrainJourney.continuous(nodes, branches)) return;
    var resolved = new ArrayList<Couple<TrackNode>>();
    int branch = 0;
    for (int i = 0; i < nodes.length; i += 2) {
      var a = train.graph.getNode(nodes[i]);
      var b = train.graph.getNode(nodes[i + 1]);
      if (a == null || b == null || TrackNetwork.connections(train.graph, a).get(b) == null) return;
      if (branch < branches.length && branches[branch] == i / 2) {
        resolved.add(Couple.create(a, b));
        branch++;
      }
    }
    // At the platform, native navigation has finished but the schedule is POST_TRANSIT.
    if (tag.getBoolean("Waiting")) return;
    path.addAll(resolved);
    destination = station;
    distanceToDestination = remaining;
    distanceStartedAt = start;
    destinationBehindTrain = tag.getBoolean("BehindTrain");
  }

  private static boolean ready(CarriageBogey bogey) {
    return bogey == null || bogey.leading().edge != null && bogey.trailing().edge != null;
  }

  @Override
  public void tick(Level level) {}

  @Override
  public void cancelNavigation() {}

  @Override
  public double startNavigation(DiscoveredPath path) {
    return -1;
  }

  @Override
  public void read(CompoundTag tag, TrackGraph graph, DimensionPalette palette) {}
}
