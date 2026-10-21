package com.guidedrailways;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.joml.Vector3d;

/** Projects the elected controller's state. It never plans, drives, reserves or advances tasks. */
final class NativeTrainJourney {
  private CompoundTag program;
  private AutomaticTrains.Plan cachedPlan;
  private int cachedLeg = -1;
  private int[] cachedPath = new int[0];
  private int[] cachedBranches = new int[0];
  private final ArrivalEstimate arrival = new ArrivalEstimate();

  CompoundTag capture(TrainConsists.Group group, CompoundTag train) {
    var out = new CompoundTag();
    var driver = group.units().getFirst().driver;
    if (driver == null || driver.isRemoved()) return clear(out);
    var c = driver.schedule;
    var data = c.present() ? c.item.get(AllDataComponents.TRAIN_SCHEDULE) : null;
    if (data == null) return clear(out);
    // The schedule component is replaced when its saved progress changes. Reuse unchanged data.
    if (!data.equals(program)) program = data.copy();
    out.put("Program", program);
    out.putInt("Entry", c.entry);
    out.putInt("Status", c.status);
    out.putBoolean("Paused", !c.running || c.manualOverride || c.resumePending);
    out.putBoolean("Completed", c.status == ScheduleController.FINISHED);
    out.putBoolean("Waiting", c.waiting);
    out.putString("Title", c.currentTitle);
    out.putIntArray("Progress", c.progress);
    var contexts = new ListTag();
    for (var context : c.contexts) contexts.add(context.copy());
    out.put("Contexts", contexts);

    var p = c.plan;
    var members =
        group.units().stream()
            .map(u -> u.bogie.getUniqueId())
            .collect(java.util.stream.Collectors.toSet());
    if (out.getBoolean("Paused")
        || out.getBoolean("Completed")
        || c.routeRetry
        || train.getBoolean("Derailed")
        || !train.hasUUID("Graph")
        || p == null
        || !train.getUUID("Graph").equals(p.network.graph)
        || c.members == null
        || !members.equals(c.members)
        || !group.units().contains(p.head)
        || !p.network.live(p.route)
        || !Double.isFinite(c.remaining)
        || p.leg < 0
        || p.leg >= p.route.edges().size()) {
      forgetRoute();
      return out;
    }
    var navigation = new CompoundTag();
    navigation.putUUID("Destination", p.route.goal().id());
    navigation.putDouble("Remaining", Math.max(0, c.remaining));
    navigation.putDouble("StartedAt", p.route.distance());
    navigation.putBoolean("Waiting", c.waiting);
    navigation.putInt(
        "ArrivalTicks",
        arrival.sample(
            p,
            driver.getLevel().getGameTime(),
            Math.max(0, c.remaining),
            Math.abs(train.getDouble("Speed")),
            c.status == ScheduleController.TRAVELLING && !c.waiting));
    var parts = train.getList("Carriages", Tag.TAG_COMPOUND);
    var front = parts.getCompound(0);
    var axis =
        PhysicalTrainProxy.vector(front, "Ahead")
            .subtract(PhysicalTrainProxy.vector(front, "Back"));
    var travel =
        new Vector3d(0, 0, 1)
            .rotate(p.head.bogie.logicalPose().orientation())
            .mul(p.signs.get(p.head.bogie.getUniqueId()));
    navigation.putBoolean(
        "BehindTrain", axis.x * travel.x + axis.y * travel.y + axis.z * travel.z < 0);
    if (cachedPlan != p || cachedLeg != p.leg) {
      // Native currentPath contains future outgoing edges; the occupied edge is already known.
      var edges = p.route.edges().subList(p.leg + 1, p.route.edges().size());
      cachedPath = new int[edges.size() * 2];
      var branches = new ArrayList<Integer>();
      for (int i = 0; i < edges.size(); i++) {
        cachedPath[i * 2] = edges.get(i).from();
        cachedPath[i * 2 + 1] = edges.get(i).to();
        if (p.network.exits(p.route.edges().get(p.leg + i)).size() > 1) branches.add(i);
      }
      cachedBranches = branches.stream().mapToInt(Integer::intValue).toArray();
      cachedPlan = p;
      cachedLeg = p.leg;
    }
    navigation.putIntArray("Path", cachedPath);
    navigation.putIntArray("Branches", cachedBranches);
    out.put("Navigation", navigation);
    return out;
  }

  private CompoundTag clear(CompoundTag out) {
    reset();
    return out;
  }

  void reset() {
    program = null;
    forgetRoute();
  }

  private void forgetRoute() {
    arrival.reset();
    cachedPlan = null;
    cachedLeg = -1;
    cachedPath = new int[0];
    cachedBranches = new int[0];
  }

  static boolean continuous(int[] path, int[] branches) {
    if (path.length % 2 != 0) return false;
    for (int i = 0; i < path.length; i += 2) {
      if (path[i] == path[i + 1] || i > 0 && path[i] != path[i - 1]) return false;
    }
    int previous = -1;
    for (int branch : branches) {
      if (branch <= previous || branch < 0 || branch >= path.length / 2) return false;
      previous = branch;
    }
    return true;
  }
}
