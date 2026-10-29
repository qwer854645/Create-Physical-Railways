package com.guidedrailways;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/** A controller issues bounded drive demands to all bogies in one live coupled consist. */
final class AutomaticTrains {
  static final class Plan {
    final AutomaticNavigation network;
    final ScheduleRoute.Route route;
    final VehiclePhysics.Unit head;
    final int end;
    final Map<UUID, Integer> signs;
    final int direction;
    int leg;
    long nextLoaded;
    boolean loaded;

    Plan(
        AutomaticNavigation n,
        ScheduleRoute.Route r,
        VehiclePhysics.Unit h,
        int end,
        Map<UUID, Integer> signs,
        int direction) {
      network = n;
      route = r;
      head = h;
      this.end = end;
      this.signs = signs;
      this.direction = direction;
    }

    int choose(TrackNetwork.Path path, int node, int from) {
      return path.graph().equals(network.graph) ? route.next(from, node, leg) : -1;
    }
  }

  record Basis(Map<UUID, Integer> signs, VehiclePhysics.Unit head, int end) {}

  private static Vector3d heading(VehiclePhysics.Unit u) {
    return new Vector3d(0, 0, 1)
        .rotate(
            SubLevelPhysicsSystem.get(u.bogie.getLevel())
                .getPipeline()
                .readPose(u.bogie, new Pose3d())
                .orientation());
  }

  private static Vector3d consoleHeading(DriverConsoleEntity e) {
    var f = e.getBlockState().getValue(DriverConsoleBlock.FACING);
    var body = DriverControls.body(e);
    return new Vector3d(f.getStepX(), 0, f.getStepZ())
        .rotate(
            SubLevelPhysicsSystem.get(body.getLevel())
                .getPipeline()
                .readPose(body, new Pose3d())
                .orientation());
  }

  static void tick(
      ServerLevel level,
      Collection<VehiclePhysics.Unit> input,
      Collection<DriverConsoleEntity> consoles) {
    var previous = new HashSet<VehiclePhysics.Unit>();
    for (var u : input) {
      if (u.command != null) previous.add(u);
      u.command = null;
    }
    var processed = new HashSet<ScheduleController>();
    for (var component : TrainConsists.get(level).groups()) {
      var group = new ArrayList<>(component.units());
      if (group.isEmpty()) continue;
      var links = component.links();
      var master = group.getFirst().driver;
      if (master == null) continue;
      var controller = master.schedule;
      processed.add(controller);
      stats(master, group);
      if (master.emergency) {
        if (controller.present()) controller.pause(ScheduleController.EMERGENCY);
        hold(group, controller, true);
        continue;
      }
      if (controller.resumePending()) {
        var restored = new HashSet<UUID>();
        for (var u : group) restored.add(u.bogie.getUniqueId());
        if (!controller.restore(
            level, restored, CouplerConnections.cargoComplete(level, component.members()))) {
          hold(group, controller, false);
          continue;
        }
      }
      if (!controller.present()) {
        var orientation = basis(master, group, links, master.direction == 0 ? 1 : master.direction);
        if (orientation == null) {
          hold(group, controller, false);
          continue;
        }
        bind(
            group,
            master,
            null,
            orientation.signs,
            master.direction == 0 ? 0 : available(group, master),
            master.notch,
            false,
            !master.enabled);
        continue;
      }
      if (master.active && !master.observing) controller.manual(master);
      if (controller.manualOverride()) {
        var source = controller.manualSource();
        if (source == null || source.isRemoved() || DriverControls.body(source) == null) {
          controller.pause(ScheduleController.PAUSED);
          hold(group, controller, false);
          continue;
        }
        stats(source, group);
        var basis = basis(source, group, links, source.direction == 0 ? 1 : source.direction);
        if (basis == null) {
          controller.pause(ScheduleController.CONFLICT);
          hold(group, controller, false);
          continue;
        }
        bind(
            group,
            source,
            null,
            basis.signs,
            source.direction == 0 ? 0 : available(group, source),
            source.notch,
            false,
            !source.enabled);
        continue;
      }
      if (!controller.running()) {
        hold(group, controller, false);
        continue;
      }
      var members = new HashSet<UUID>();
      for (var u : group) members.add(u.bogie.getUniqueId());
      var cargo = CargoStorage.consist(level, group.getFirst().carrier);
      var membership = controller.acceptMembers(members, cargo.complete());
      if (membership == ScheduleReloadGate.Result.WAIT) {
        controller.state(ScheduleController.UNLOADED);
        hold(group, controller, false);
        continue;
      }
      if (membership == ScheduleReloadGate.Result.CHANGED) {
        controller.pause(ScheduleController.CHANGED);
        hold(group, controller, false);
        continue;
      }
      if (group.stream().anyMatch(u -> u.detached)) {
        controller.pause(ScheduleController.TRACK_MISSING);
        hold(group, controller, false);
        continue;
      }
      if (!controller.prepareInstructions()) {
        hold(group, controller, false);
        continue;
      }
      if (!controller.acceptCargoBodies(cargo.bodies().keySet())) {
        controller.pause(ScheduleController.CHANGED);
        hold(group, controller, false);
        continue;
      }
      if (controller.takeBrakeRelease()) for (var u : group) brake(u, false);
      double cap = SchedulePrelude.ceiling(available(group, master), controller.throttle());
      if (cap <= 0) {
        controller.state(ScheduleController.NO_POWER);
        hold(group, controller, false);
        continue;
      }
      try {
        drive(level, group, links, controller, cap);
      } catch (RuntimeException error) {
        controller.pause(ScheduleController.UNSUPPORTED);
        hold(group, controller, false);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .error(
                "[AutomaticTrain] paused after invalid schedule/route console={}",
                controller.owner.getBlockPos(),
                error);
      }
    }
    for (var console : consoles)
      if (console.schedule.running() && !processed.contains(console.schedule))
        console.schedule.pause(ScheduleController.TRACK_MISSING);
    // A split or removed controller must not leave its former motorised trailers running.
    for (var u : previous)
      if (u.command == null && !u.bogie.isRemoved() && !u.mount.isRemoved()) brake(u, true);
  }

  private static void stats(DriverConsoleEntity e, List<VehiclePhysics.Unit> group) {
    e.available = available(group, e);
    e.supports = group.size();
    e.powered = 0;
    e.guided = 0;
    for (var u : group) {
      if (Math.abs(VehicleTraction.inputRpm(u)) > .001) e.powered++;
      if (!u.detached) e.guided++;
    }
  }

  private static void brake(VehiclePhysics.Unit u, boolean value) {
    if (u.mount.braking != value) {
      u.mount.braking = value;
      u.mount.setChanged();
      u.mount.sendData();
    }
  }

  private static double available(List<VehiclePhysics.Unit> group, DriverConsoleEntity console) {
    double cap = Double.POSITIVE_INFINITY;
    for (var u : group)
      if (Math.abs(VehicleTraction.inputRpm(u)) > .001)
        cap = Math.min(cap, console.targetSpeed(VehicleTraction.inputRpm(u)));
    return Double.isFinite(cap) ? cap : 0;
  }

  private static double brakeFraction(List<VehiclePhysics.Unit> group) {
    double supportedMass = 0;
    var carriers = new HashSet<UUID>();
    for (var u : group) {
      supportedMass += u.bogie.getMassTracker().getMass();
      if (carriers.add(u.carrier.getUniqueId()))
        supportedMass += u.carrier.getMassTracker().getMass();
    }
    var consist =
        TrainConsists.get(group.getFirst().carrier.getLevel())
            .group(group.getFirst().carrier.getUniqueId());
    return Math.clamp(supportedMass / Math.max(.001, consist.mass()), .001, 1);
  }

  private static double maximumSpeed(List<VehiclePhysics.Unit> group) {
    double speed = 0;
    for (var u : group)
      speed =
          Math.max(
              speed,
              SubLevelPhysicsSystem.get(u.bogie.getLevel())
                  .getPipeline()
                  .getLinearVelocity(u.bogie, new Vector3d())
                  .length());
    return speed;
  }

  private static void hold(
      List<VehiclePhysics.Unit> group, ScheduleController c, boolean emergency) {
    c.visual(-4, c.owner.direction);
    for (var u : group)
      u.command =
          new TrainCommand(
              c.owner, c.running() ? c : null, TrainCommand.Mode.HOLD, 1, 0, -4, emergency, true);
  }

  private static void bind(
      List<VehiclePhysics.Unit> group,
      DriverConsoleEntity owner,
      ScheduleController navigation,
      Map<UUID, Integer> signs,
      double cap,
      int notch,
      boolean emergency,
      boolean hold) {
    // Auto align drives through the same command as a manual lever, but with its own ceiling: the lever only has
    // notches, and even the first notch creeps far above the speed alignment is permitted to use.
    if (navigation == null && owner != null && owner.aligning()) {
      double offset = owner.alignOffset();
      double speed = 0;
      for (var u : group) speed = Math.max(speed, Math.abs(u.speed));
      if (Double.isNaN(offset)) {
        owner.stopAligning();
      } else {
        var align = AutoAlign.step(offset, speed, true, owner.emergency);
        if (align.active()) {
          cap = Math.abs(align.targetSpeed());
          notch = align.targetSpeed() > 0 ? 1 : -1;
        } else {
          if (align.finished()) owner.stopAligning();
          cap = 0;
          notch = 0;
        }
      }
    }
    if (navigation != null) navigation.visual(notch, navigation.plan().direction);
    for (var u : group)
      u.command =
          new TrainCommand(
              owner,
              navigation,
              navigation == null ? TrainCommand.Mode.MANUAL : TrainCommand.Mode.SCHEDULE,
              signs.getOrDefault(u.bogie.getUniqueId(), 1),
              cap,
              notch,
              emergency,
              hold);
  }

  static Basis basis(
      DriverConsoleEntity console,
      List<VehiclePhysics.Unit> group,
      List<CouplerConnections.BodyLink> links,
      int direction) {
    var root = DriverControls.body(console);
    if (root == null) return null;
    var axes = new HashMap<UUID, Vector3d>();
    var centers = new HashMap<UUID, Vector3d>();
    var counts = new HashMap<UUID, Integer>();
    for (var u : group) {
      var id = u.carrier.getUniqueId();
      axes.putIfAbsent(id, heading(u));
      centers
          .computeIfAbsent(id, k -> new Vector3d())
          .add(VehiclePhysics.world(u.bogie, u.localCenter));
      counts.merge(id, 1, Integer::sum);
    }
    for (var id : centers.keySet()) centers.get(id).div(counts.get(id));
    for (var l : links)
      for (var b : List.of(l.a(), l.b())) {
        var id = b.getUniqueId();
        axes.putIfAbsent(
            id,
            new Vector3d(0, 0, 1)
                .rotate(
                    SubLevelPhysicsSystem.get(b.getLevel())
                        .getPipeline()
                        .readPose(b, new Pose3d())
                        .orientation()));
        centers.putIfAbsent(id, VehiclePhysics.world(b, b.getMassTracker().getCenterOfMass()));
      }
    axes.put(root.getUniqueId(), consoleHeading(console));
    var orientedLinks = new ArrayList<ConsistDirection.Link>();
    for (var l : links) {
      var a = l.a().getUniqueId();
      var b = l.b().getUniqueId();
      var pa = VehiclePhysics.world(l.a(), l.anchorA()).sub(centers.get(a));
      var pb = VehiclePhysics.world(l.b(), l.anchorB()).sub(centers.get(b));
      orientedLinks.add(
          new ConsistDirection.Link(
              a, b, pa.dot(axes.get(a)) < 0 ? -1 : 1, pb.dot(axes.get(b)) < 0 ? -1 : 1));
    }
    var orientations = ConsistDirection.orient(root.getUniqueId(), direction, orientedLinks);
    if (orientations == null) return null;
    var signs = new HashMap<UUID, Integer>();
    for (var u : group) {
      var id = u.carrier.getUniqueId();
      var travel = new Vector3d(axes.get(id)).mul(orientations.getOrDefault(id, direction));
      signs.put(u.bogie.getUniqueId(), heading(u).dot(travel) < 0 ? -1 : 1);
    }
    UUID front = null;
    for (var id : orientations.keySet()) {
      var travel = new Vector3d(axes.get(id)).mul(orientations.get(id));
      int degree = 0;
      boolean leading = true;
      for (var l : links)
        if (l.a().getUniqueId().equals(id) || l.b().getUniqueId().equals(id)) {
          degree++;
          boolean a = l.a().getUniqueId().equals(id);
          var socket = VehiclePhysics.world(a ? l.a() : l.b(), a ? l.anchorA() : l.anchorB());
          if (socket.sub(centers.get(id)).dot(travel) > 0) leading = false;
        }
      if (degree <= 1 && leading) {
        if (front != null) return null;
        front = id;
      }
    }
    if (front == null) return null;
    var distance = new HashMap<UUID, Integer>();
    var queue = new ArrayDeque<UUID>();
    distance.put(front, 0);
    queue.add(front);
    while (!queue.isEmpty()) {
      var id = queue.remove();
      for (var l : links) {
        UUID next =
            l.a().getUniqueId().equals(id)
                ? l.b().getUniqueId()
                : l.b().getUniqueId().equals(id) ? l.a().getUniqueId() : null;
        if (next != null && !distance.containsKey(next)) {
          distance.put(next, distance.get(id) + 1);
          queue.add(next);
        }
      }
    }
    VehiclePhysics.Unit head = null;
    double best = -Double.MAX_VALUE;
    int hops = Integer.MAX_VALUE;
    for (var u : group) {
      var id = u.carrier.getUniqueId();
      int d = distance.getOrDefault(id, Integer.MAX_VALUE);
      var travel = new Vector3d(axes.get(id)).mul(orientations.getOrDefault(id, direction));
      double rank = VehiclePhysics.world(u.bogie, u.localCenter).sub(centers.get(id)).dot(travel);
      if (d < hops || d == hops && rank > best) {
        head = u;
        hops = d;
        best = rank;
      }
    }
    if (head == null) return null;
    return new Basis(signs, head, signs.get(head.bogie.getUniqueId()) > 0 ? 0 : 1);
  }

  private static TrackNetwork.Sample sample(ServerLevel level, VehiclePhysics.Unit u, int end) {
    var current = u.samples[end];
    var p = VehiclePhysics.world(u.bogie, u.axle(end));
    return current == null
        ? TrackNetwork.nearest(level, p, heading(u), null, 1, u.mount.gauge)
        : TrackNetwork.resample(level, current, p, heading(u), 1);
  }

  private static Plan plan(
      ServerLevel level,
      List<VehiclePhysics.Unit> group,
      List<CouplerConnections.BodyLink> links,
      ScheduleController c,
      Set<UUID> packageTargets) {
    var instruction = c.instruction();
    if (instruction == null) return null;
    Plan best = null;
    boolean stopped = maximumSpeed(group) < .15;
    int forward = c.owner.actual < -.05 ? -1 : 1;
    for (int direction : new int[] {forward, -forward}) {
      if (direction != forward && !stopped) continue;
      var basis = basis(c.owner, group, links, direction);
      if (basis == null) continue;
      var head = basis.head;
      var sample = sample(level, head, basis.end);
      if (sample == null) continue;
      var network =
          new AutomaticNavigation(
              level,
              sample.path().graph(),
              group.stream().map(u -> u.mount.gauge).collect(java.util.stream.Collectors.toSet()));
      var travel = new Vector3d(sample.tangent()).mul(basis.signs.get(head.bogie.getUniqueId()));
      var start = network.direction(sample, travel);
      var goals =
          instruction
                  instanceof
                  com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
                          destination
              ? network.goals(destination, c.waiting() ? c.station() : null)
              : network.goals(
                  stop -> packageTargets.contains(stop.id), c.waiting() ? c.station() : null);
      var route =
          ScheduleRoute.find(
              network,
              start,
              network.offset(sample, start),
              goals,
              AutomaticDriveModel.STOP_TOLERANCE);
      if (route != null && (best == null || route.distance() < best.route.distance() - 1e-6))
        best = new Plan(network, route, head, basis.end, basis.signs, direction);
    }
    return best;
  }

  private static void drive(
      ServerLevel level,
      List<VehiclePhysics.Unit> group,
      List<CouplerConnections.BodyLink> links,
      ScheduleController c,
      double cap) {
    if (c.routeRetry()) {
      c.invalidateRoute(false);
      c.state(ScheduleController.PLANNING);
      hold(group, c, false);
      return;
    }
    if (c.plan() != null && !c.plan().network.live(c.plan().route)) {
      c.invalidateRoute(true);
    }
    if (c.plan() == null) {
      if (!c.beginPlanning(level.getGameTime())) {
        hold(group, c, false);
        return;
      }
      Set<UUID> packageTargets = Set.of();
      if (c.packageInstruction()) {
        if (c.waiting() && c.station() != null) packageTargets = Set.of(c.station());
        else {
          var selection = PackageMail.targets(level, c);
          packageTargets = selection.stations();
          if (selection.decision() != PackageTask.Decision.VISIT) {
            hold(group, c, false);
            switch (selection.decision()) {
              case SKIP -> c.advance();
              case UNLOADED -> c.state(ScheduleController.UNLOADED);
              case NO_TARGET -> c.state(ScheduleController.PACKAGE_TARGET);
              default -> throw new IllegalStateException("Invalid package selection");
            }
            return;
          }
        }
      }
      if (!c.acceptPlan(plan(level, group, links, c, packageTargets))) {
        c.state(ScheduleController.NO_ROUTE);
        hold(group, c, false);
        return;
      }
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[AutomaticRoute] target={} distance={} edges={} leadingBogie={} end={}",
              c.plan().route.goal().name(),
              c.plan().route.distance(),
              c.plan().route.edges().size(),
              c.plan().head.bogie.getUniqueId(),
              c.plan().end);
    }
    var p = c.plan();
    var sample = sample(level, p.head, p.end);
    if (sample == null) {
      c.pause(ScheduleController.TRACK_MISSING);
      hold(group, c, false);
      return;
    }
    var observed =
        p.network.direction(
            sample, new Vector3d(sample.tangent()).mul(p.signs.get(p.head.bogie.getUniqueId())));
    int found = p.route.progress(observed, p.leg);
    if (found < 0) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .warn(
              "[AutomaticRouteMismatch] console={} bogie={} observed={} leg={} expected={} target={}",
              c.owner.getBlockPos(),
              p.head.bogie.getUniqueId(),
              observed,
              p.leg,
              p.route.edges().get(p.leg),
              p.route.goal().id());
      c.invalidateRoute(false);
      c.state(ScheduleController.PLANNING);
      hold(group, c, false);
      return;
    }
    p.leg = found;
    var edge = p.route.edges().get(p.leg);
    double offset = p.network.offset(sample, edge);
    double remaining = p.route.remaining(p.network, p.leg, offset);
    c.distance(remaining);
    if (level.getGameTime() >= p.nextLoaded) {
      p.loaded = p.network.loaded(p.route, p.leg, offset);
      p.nextLoaded = level.getGameTime() + 10;
    }
    if (!p.loaded) {
      c.state(ScheduleController.UNLOADED);
      hold(group, c, false);
      return;
    }
    double maxSpeed = maximumSpeed(group);
    boolean arrived = AutomaticDriveModel.arrived(remaining, maxSpeed);
    if (c.waiting() && !arrived) {
      c.leaveArrival();
    }
    if (arrived) {
      hold(group, c, false);
      if (c.arrivalStable()) {
        if (c.exchangeMail(level) && c.tickConditions(level)) c.advance();
      }
      return;
    }
    c.travelling();
    if (CargoStorage.blockedByDock(level, group.getFirst().carrier)) {
      c.state(ScheduleController.DOCK_WAIT);
      hold(group, c, false);
      return;
    }
    if (remaining < -AutomaticDriveModel.STOP_TOLERANCE) {
      c.pause(ScheduleController.OVERSHOOT);
      hold(group, c, false);
      return;
    }
    double speed =
        SubLevelPhysicsSystem.get(level)
                .getPipeline()
                .getLinearVelocity(p.head.bogie, new Vector3d())
                .dot(sample.tangent())
            * p.signs.get(p.head.bogie.getUniqueId());
    double braking = TrainConfig.BRAKING.get() * brakeFraction(group);
    double nose =
        PhysicalSignals.nose(
            p.head,
            sample.position(),
            new Vector3d(sample.tangent()).mul(p.signs.get(p.head.bogie.getUniqueId())));
    double horizon = nose + cap * cap / Math.max(.01, braking) + cap * .3 + 8;
    double signal = PhysicalSignals.limit(level, c.owner, p, offset, horizon);
    double signalRoom = signal - nose - .5;
    boolean waitingSignal = Double.isFinite(signal) && signalRoom < remaining;
    var demand =
        AutomaticDriveModel.demand(waitingSignal ? signalRoom : remaining, speed, cap, braking);
    c.state(waitingSignal ? ScheduleController.SIGNAL_WAIT : ScheduleController.TRAVELLING);
    bind(group, c.owner, c, p.signs, demand.ceiling(), demand.notch(), false, false);
    if (level.getGameTime() % 20 == 0)
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[AutomaticDrive] target={} remaining={} speed={} ceiling={} notch={} bogies={} signalRoom={} nose={}",
              p.route.goal().name(),
              remaining,
              speed,
              demand.ceiling(),
              demand.notch(),
              group.size(),
              signalRoom,
              nose);
  }
}
