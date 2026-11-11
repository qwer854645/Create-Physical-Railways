package com.physicalrailways;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/** Transient fault containment. Never deletes train, inventory, pairing or schedule data. */
final class TrainFaults {
  static final long INCIDENT_TICKS = 1200;
  static final long REPORT_TICKS = 200;

  private static final class Incident {
    final RecoveryWindow window = new RecoveryWindow();
    final Set<UUID> expected = new HashSet<>();
    long seen, nextLog;
    boolean fault;
  }

  private static final class World {
    Map<UUID, TrainConsists.Group> groups = Map.of();
    final Map<UUID, Incident> incidents = new HashMap<>();
    final Map<String, RecoveryWindow> phases = new HashMap<>();
  }

  private static final Map<ServerLevel, World> WORLDS = new WeakHashMap<>();

  private static Set<UUID> footprint(TrainConsists.Group group) {
    var ids = new HashSet<>(group.members());
    for (var u : group.units()) ids.add(u.bogie.getUniqueId());
    return ids;
  }

  static void begin(ServerLevel level, TrainConsists.Snapshot snapshot) {
    var world = WORLDS.computeIfAbsent(level, k -> new World());
    var previousGroups = world.groups;
    world.groups = snapshot.byBody();
    long now = level.getGameTime();
    BodyGuard.beginStep(now);
    for (var group : snapshot.groups()) {
      Incident incident = null;
      for (var id : group.members()) {
        var found = world.incidents.get(id);
        if (found != null && found.window.blocked()) {
          incident = found;
          break;
        }
      }
      if (incident == null) {
        // A live drawbar can outlast an unloaded axle. Cargo completeness alone cannot detect it.
        for (var id : group.members()) {
          var previous = previousGroups.get(id);
          if (previous != null
              && (!previous.members().equals(group.members())
                  || !previous.units().equals(group.units()))
              && !footprint(group).containsAll(footprint(previous))) {
            incident = attach(world, previous);
            incident.window.unavailable(now);
            break;
          }
        }
      }
      if (incident == null && !world.phases.isEmpty()) {
        incident = attach(world, group);
        incident.window.unavailable(now);
      }
      if (incident != null) {
        for (var id : group.members()) {
          var other = world.incidents.get(id);
          if (other != null && other != incident && other.window.blocked()) {
            incident.expected.addAll(other.expected);
            incident.fault |= other.fault;
            incident.window.unavailable(now);
          }
        }
        incident.seen = now;
        for (var id : group.members()) world.incidents.put(id, incident);
        if (!world.phases.isEmpty()) {
          incident.window.unavailable(now);
          continue;
        }
        if (!incident.window.probe(now)) continue;
      }
      try {
        boolean complete = CouplerConnections.cargoComplete(level, group.members());
        for (var u : group.units()) complete &= u.ready(now);
        if (incident != null) {
          complete &= footprint(group).containsAll(incident.expected);
          var physics = SubLevelPhysicsSystem.get(level);
          for (var body : group.bodies().values()) complete &= BodyGuard.readable(physics, body);
          for (var u : group.units()) complete &= BodyGuard.readable(physics, u);
          if (complete) {
            if (incident.window.healthy(now)) incident.fault = false;
          } else incident.window.unavailable(now);
        } else if (!complete) {
          incident = attach(world, group);
          incident.window.unavailable(now);
          incident.seen = now;
        }
      } catch (RuntimeException error) {
        fail(level, group, "readiness", error);
      }
    }
    // UUID aliases only: do not retain unloaded bodies or old worlds indefinitely.
    world
        .incidents
        .entrySet()
        .removeIf(e -> now < e.getValue().seen || now - e.getValue().seen > INCIDENT_TICKS);
  }

  private static Incident attach(World world, TrainConsists.Group group) {
    Incident incident = null;
    for (var id : group.members()) {
      var old = world.incidents.get(id);
      if (old != null) {
        incident = old;
        break;
      }
    }
    if (incident == null) incident = new Incident();
    incident.expected.addAll(footprint(group));
    for (var id : group.members()) world.incidents.put(id, incident);
    return incident;
  }

  static boolean blocked(ServerLevel level, UUID body) {
    var world = WORLDS.get(level);
    var incident = world == null ? null : world.incidents.get(body);
    return incident != null && incident.window.blocked();
  }

  static boolean blocked(ServerLevel level, TrainConsists.Group group) {
    return group.members().stream().anyMatch(id -> blocked(level, id));
  }

  static ConsoleStatus reason(ServerLevel level, UUID body) {
    var world = WORLDS.get(level);
    var incident = world == null ? null : world.incidents.get(body);
    return incident == null || !incident.window.blocked()
        ? null
        : incident.fault ? ConsoleStatus.RUNTIME_FAULT : ConsoleStatus.RESTORING;
  }

  static void fail(
      ServerLevel level, VehiclePhysics.Unit unit, String stage, RuntimeException error) {
    var world = WORLDS.get(level);
    var group = world == null ? null : world.groups.get(unit.carrier.getUniqueId());
    if (group == null)
      group =
          new TrainConsists.Group(
              Set.of(unit.carrier.getUniqueId()),
              List.of(unit),
              Map.of(unit.carrier.getUniqueId(), unit.carrier),
              List.of());
    fail(level, group, stage, error);
  }

  static void fail(ServerLevel level, UUID body, String stage, RuntimeException error) {
    var world = WORLDS.get(level);
    var group = world == null ? null : world.groups.get(body);
    if (group != null) fail(level, group, stage, error);
  }

  static void acknowledge(ServerLevel level, UUID body) {
    var world = WORLDS.get(level);
    if (world == null) return;
    var incident = world.incidents.get(body);
    var group = world.groups.get(body);
    if (incident == null || !incident.window.blocked() || group == null) return;
    retarget(world, group, incident, level.getGameTime());
  }

  /** An intentional split must not leave two new trains sharing one readiness clock. */
  private static void retarget(
      World world, TrainConsists.Group group, Incident previous, long now) {
    var next = new Incident();
    next.fault = previous.fault;
    next.nextLog = previous.nextLog;
    next.seen = now;
    next.expected.addAll(footprint(group));
    next.window.unavailable(now);
    for (var id : group.members()) world.incidents.put(id, next);
  }

  static boolean phaseReady(ServerLevel level, String stage) {
    var world = WORLDS.get(level);
    var gate = world == null ? null : world.phases.get(stage);
    return gate == null || gate.probe(level.getGameTime());
  }

  static void phaseHealthy(ServerLevel level, String stage) {
    var world = WORLDS.get(level);
    var gate = world == null ? null : world.phases.get(stage);
    if (gate != null && gate.healthy(level.getGameTime())) world.phases.remove(stage);
  }

  /**
   * No trustworthy train identity exists for a shared-service fault: hold this world
   * conservatively.
   */
  static void phaseFailed(ServerLevel level, String stage, RuntimeException error) {
    var world = WORLDS.computeIfAbsent(level, k -> new World());
    world.phases.computeIfAbsent(stage, k -> new RecoveryWindow()).failed(level.getGameTime());
    var affected = new HashSet<>(world.groups.values());
    if (affected.isEmpty())
      org.slf4j.LoggerFactory.getLogger(PhysicalRailways.ID)
          .error("[TrainFault] stage={} action=hold_world_and_retry", stage, error);
    for (var group : affected) fail(level, group, stage, error);
    for (var u : VehiclePhysics.cargoUnits(level))
      if (!world.groups.containsKey(u.carrier.getUniqueId())) fail(level, u, stage, error);
  }

  static void brakeHeld(SubLevelPhysicsSystem physics, double dt) {
    for (var u : VehiclePhysics.cargoUnits(physics.getLevel()))
      if (blocked(physics.getLevel(), u.carrier.getUniqueId())) brake(physics, u, dt);
  }

  static void fail(
      ServerLevel level, TrainConsists.Group group, String stage, RuntimeException error) {
    var world = WORLDS.computeIfAbsent(level, k -> new World());
    var incident = attach(world, group);
    long now = level.getGameTime();
    incident.window.failed(now);
    incident.seen = now;
    boolean first = !incident.fault;
    incident.fault = true;
    // Clear commands before touching any renderer, block entity or schedule callbacks.
    for (var u : group.units()) {
      u.command = null;
      u.tractionMass = 0;
      u.trainPowered = false;
    }
    if (first) {
      var consoles = new HashSet<DriverConsoleEntity>();
      for (var u : group.units()) if (u.driver != null) consoles.add(u.driver);
      for (var console : consoles) {
        console.enabled = false;
        console.notch = -4;
        console.active = false;
        console.operator = null;
        try {
          if (console.schedule.present()) console.schedule.pause(ScheduleController.RUNTIME_FAULT);
        } catch (RuntimeException secondary) {
          if (secondary != error) error.addSuppressed(secondary);
        }
        try {
          console.clearRouteSelection();
          console.setChanged();
        } catch (RuntimeException secondary) {
          if (secondary != error) error.addSuppressed(secondary);
        }
      }
    }
    if (now >= incident.nextLog) {
      incident.nextLog = now + REPORT_TICKS;
      org.slf4j.LoggerFactory.getLogger(PhysicalRailways.ID)
          .error(
              "[TrainFault] stage={} bodies={} action=hold_and_retry",
              stage,
              group.members(),
              error);
    }
  }

  static void changed(ServerLevel level, Set<UUID> affected) {
    var world = WORLDS.get(level);
    if (world == null) return;
    var reset = new HashSet<Incident>();
    for (var id : affected) {
      var incident = world.incidents.get(id);
      if (incident != null) reset.add(incident);
    }
    var snapshot = TrainConsists.get(level);
    // Explicit player topology changes accept a new baseline; passive unloading does not.
    world.groups = snapshot.byBody();
    if (reset.isEmpty()) return;
    for (var group : snapshot.groups()) {
      Incident previous = null;
      for (var id : group.members()) {
        var incident = world.incidents.get(id);
        if (reset.contains(incident)) {
          if (previous == null || incident.fault) previous = incident;
        }
      }
      if (previous != null) retarget(world, group, previous, level.getGameTime());
    }
  }

  /** Independent braking path; avoids route queries and never recreates broken guide joints. */
  static void brake(SubLevelPhysicsSystem physics, VehiclePhysics.Unit u, double dt) {
    if (!(dt > 0 && Double.isFinite(dt))) return;
    try {
      if (!BodyGuard.readable(physics, u.bogie)) return;
      var pipe = physics.getPipeline();
      var pose = pipe.readPose(u.bogie, new dev.ryanhcode.sable.companion.math.Pose3d());
      var axis = new Vector3d(0, 0, 1).rotate(pose.orientation());
      double speed = pipe.getLinearVelocity(u.bogie, new Vector3d()).dot(axis);
      double mass =
          u.mass > 0 && Double.isFinite(u.mass) ? u.mass : u.bogie.getMassTracker().getMass();
      if (!Double.isFinite(speed) || !(mass > 0 && Double.isFinite(mass))) return;
      double force = DriveModel.coastForce(speed, mass, TrainConfig.BRAKING.get(), dt);
      u.commandedForce = force;
      pipe.applyImpulse(u.bogie, u.localCenter, new Vector3d(0, 0, force * dt));
    } catch (RuntimeException error) {
      fail(physics.getLevel(), u, "safety_brake", error);
    }
  }

  static void unload(ServerLevel level) {
    WORLDS.remove(level);
  }

  private TrainFaults() {}
}
