package com.physicalrailways;

import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

/**
 * Server-side write-through adapter. Mirrored fields are sampled before projection replaces them.
 */
final class NativeTrainControl {
  private final PhysicalTrainProxy proxy;
  private ServerLevel level;
  private Set<UUID> members = Set.of(), axles = Set.of();
  private boolean baseline, consuming;
  private String name;
  private ResourceLocation icon;
  private UUID station;
  private GlobalStation destination;
  private String title;
  private int color, entry;
  private boolean paused, completed, manual;
  private boolean derailed, emergencyOwned;
  private long nextHonk, emergencyButtonAt;
  private double speed, targetSpeed, throttle;
  private com.simibubi.create.content.trains.schedule.Schedule program;
  private net.minecraft.nbt.CompoundTag programData;
  private TravellingPoint.SteerDirection steer;

  NativeTrainControl(PhysicalTrainProxy proxy) {
    this.proxy = proxy;
  }

  void bind(ServerLevel level, TrainConsists.Group group) {
    this.level = level;
    members = Set.copyOf(group.members());
    axles =
        group.units().stream()
            .map(u -> u.bogie.getUniqueId())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  void unbind() {
    level = null;
    members = axles = Set.of();
    baseline = false;
    program = null;
    programData = null;
  }

  private record Context(TrainConsists.Group group, DriverConsoleEntity driver) {}

  private Context context() {
    try {
      return resolve();
    } catch (RuntimeException error) {
      return null;
    }
  }

  private Context resolve() {
    if (level == null || !level.getServer().isSameThread() || members.isEmpty()) return null;
    var group = TrainConsists.get(level).group(members.iterator().next());
    if (group == null
        || group.units().isEmpty()
        || !members.equals(group.members())
        || !axles.equals(
            group.units().stream()
                .map(u -> u.bogie.getUniqueId())
                .collect(java.util.stream.Collectors.toSet()))
        || NativeTrainBridge.registered(level, group) != proxy
        || !CouplerConnections.cargoComplete(level, group.members())) return null;
    var driver = group.units().getFirst().driver;
    return driver == null || driver.isRemoved() || !DriverControls.isPrimary(driver)
        ? null
        : new Context(group, driver);
  }

  void projected() {
    station = proxy.currentStation;
    destination = proxy.navigation.destination;
    title = proxy.runtime.currentTitle;
    name = proxy.name == null ? "" : proxy.name.getString();
    icon = proxy.icon == null ? TrainIconType.getDefault().getId() : proxy.icon.getId();
    color = proxy.mapColorIndex;
    entry = proxy.runtime.currentEntry;
    paused = proxy.runtime.paused;
    completed = proxy.runtime.completed;
    program = proxy.runtime.schedule;
    programData =
        proxy.runtime instanceof PhysicalScheduleRuntime runtime ? runtime.programBaseline() : null;
    speed = proxy.speed;
    targetSpeed = proxy.targetSpeed;
    throttle = proxy.throttle;
    manual = proxy.manualTick;
    steer = proxy.manualSteer;
    derailed = proxy.derailed;
    baseline = true;
  }

  void stationProjected() {
    station = proxy.currentStation;
  }

  void consume() {
    consume(false);
  }

  void consume(boolean inspectProgram) {
    if (!baseline || consuming || level == null) return;
    consuming = true;
    try {
      boolean programChanged =
          proxy.runtime.schedule != program
              || inspectProgram
                  && proxy.runtime.schedule != null
                  && !Objects.equals(
                      programData, proxy.runtime.schedule.write(level.registryAccess()));
      if (!programChanged
          && Objects.equals(name, proxy.name == null ? name : proxy.name.getString())
          && Objects.equals(icon, proxy.icon == null ? icon : proxy.icon.getId())
          && color == proxy.mapColorIndex
          && entry == proxy.runtime.currentEntry
          && paused == proxy.runtime.paused
          && completed == proxy.runtime.completed
          && Objects.equals(title, proxy.runtime.currentTitle)
          && destination == proxy.navigation.destination
          && Double.compare(throttle, proxy.throttle) == 0
          && Double.compare(speed, proxy.speed) == 0
          && Double.compare(targetSpeed, proxy.targetSpeed) == 0
          && manual == proxy.manualTick
          && steer == proxy.manualSteer
          && derailed == proxy.derailed
          && Objects.equals(station, proxy.currentStation)
          && !proxy.honk
          && proxy.honkTicks <= 0) return;
      var context = context();
      if (context == null) return;
      var driver = context.driver;
      var c = driver.schedule;
      if (!Objects.equals(station, proxy.currentStation)) {
        var requested = proxy.currentStation;
        proxy.station(station);
        if (requested == null) station(null);
        else if (proxy.graph != null) {
          var target =
              proxy.graph.getPoint(
                  com.simibubi.create.content.trains.graph.EdgePointType.STATION, requested);
          if (target != null) station(target);
        }
      }
      String nextName = proxy.name == null ? name : proxy.name.getString();
      var nextIcon = proxy.icon == null ? icon : proxy.icon.getId();
      if (!Objects.equals(name, nextName)
          || !Objects.equals(icon, nextIcon)
          || color != proxy.mapColorIndex) {
        driver.nativeIdentity(nextName, nextIcon, proxy.mapColorIndex);
        identity(driver);
      }
      boolean newProgram = proxy.runtime.schedule != null && programChanged;
      if (programChanged && proxy.runtime.schedule == null) discard();
      if (newProgram
          && !driver.active
          && !driver.emergency
          && !TrainFaults.blocked(level, context.group)) {
        if (c.nativeProgram(proxy.runtime.schedule, proxy.runtime.isAutoSchedule)) {
          c.nativeEntry(Math.clamp(proxy.runtime.currentEntry, 0, c.total()));
          c.nativeThrottle(proxy.throttle);
          c.nativeTitle(proxy.runtime.currentTitle);
          if (proxy.runtime.paused) c.pause(ScheduleController.PAUSED);
        }
      }
      if (entry != proxy.runtime.currentEntry && !driver.active)
        c.nativeEntry(proxy.runtime.currentEntry);
      if (!Objects.equals(title, proxy.runtime.currentTitle))
        c.nativeTitle(proxy.runtime.currentTitle);
      if (destination != proxy.navigation.destination) {
        if (proxy.navigation.destination == null) cancel();
        else navigate(new DiscoveredPath(0, 0, List.of(), proxy.navigation.destination));
      }
      if (paused != proxy.runtime.paused) {
        if (proxy.runtime.paused) {
          driver.enabled = false;
          driver.notch = -4;
          c.pause(ScheduleController.PAUSED);
        } else if (!driver.active
            && !driver.emergency
            && !TrainFaults.blocked(level, context.group)) c.nativeResume();
      }
      if (completed != proxy.runtime.completed && proxy.runtime.completed && !driver.active)
        c.nativeEntry(c.total());
      if (Double.compare(throttle, proxy.throttle) != 0) c.nativeThrottle(proxy.throttle);
      if (proxy.derailed != derailed) {
        if (proxy.derailed) detach();
        else reattach(level);
      }
      if (Double.compare(targetSpeed, proxy.targetSpeed) != 0) speed(proxy.targetSpeed);
      else if (Double.compare(speed, proxy.speed) != 0) speed(proxy.speed);
      if (proxy.manualTick && !manual && Double.compare(targetSpeed, proxy.targetSpeed) == 0)
        speed(proxy.targetSpeed);
      if (proxy.manualTick) driver.nativeUntil = level.getGameTime() + 20;
      if ((steer != proxy.manualSteer || proxy.manualTick)
          && proxy.manualSteer != null
          && !driver.active)
        driver.routeSelection.select(
            (proxy.manualSteer == TravellingPoint.SteerDirection.LEFT
                    ? -1
                    : proxy.manualSteer == TravellingPoint.SteerDirection.RIGHT ? 1 : 0)
                * forward(driver));
      if (proxy.honk || proxy.honkTicks > 0) {
        honk();
        proxy.honk = false;
        proxy.honkTicks = 0;
      }
    } catch (RuntimeException error) {
      var group = TrainConsists.get(level).group(members.iterator().next());
      if (group != null) TrainFaults.fail(level, group, "native_control", error);
    } finally {
      // A rejected or accepted request is consumed once; it cannot reappear after a load/topology
      // change.
      proxy.manualTick = false;
      projected();
      consuming = false;
    }
  }

  private int forward(DriverConsoleEntity driver) {
    if (proxy.carriages.isEmpty()) return 1;
    var anchors = proxy.carriages.getFirst().getDimensional(level.dimension()).rotationAnchors;
    if (anchors == null) return 1;
    var front = anchors.getFirst().subtract(anchors.getSecond());
    var f = driver.getBlockState().getValue(DriverConsoleBlock.FACING);
    var body = DriverControls.body(driver);
    var heading =
        new Vector3d(f.getStepX(), 0, f.getStepZ()).rotate(body.logicalPose().orientation());
    return front.x * heading.x + front.y * heading.y + front.z * heading.z < 0 ? -1 : 1;
  }

  boolean speed(double blocksPerTick) {
    var context = context();
    if (context == null || !Double.isFinite(blocksPerTick)) return false;
    var driver = context.driver;
    double desired =
        Math.clamp(
                blocksPerTick * 20,
                -TrainConfig.MAX_TARGET_SPEED.get(),
                TrainConfig.MAX_TARGET_SPEED.get())
            * forward(driver);
    if (desired != 0
        && (driver.active
            || driver.emergency
            || TrainFaults.blocked(level, context.group)
            || context.group.units().stream().anyMatch(u -> u.detached))) return false;
    driver.nativeUntil = Long.MAX_VALUE;
    driver.schedule.manual(driver);
    driver.nativeSpeed = desired;
    driver.direction = desired == 0 ? driver.direction : desired < 0 ? -1 : 1;
    driver.enabled = desired != 0;
    driver.notch = desired == 0 ? -4 : DriverModel.MAX_NOTCH;
    driver.observing = false;
    driver.setChanged();
    return true;
  }

  boolean schedule(com.simibubi.create.content.trains.schedule.Schedule value, boolean auto) {
    var context = context();
    return context != null
        && value != null
        && !context.driver.active
        && !context.driver.emergency
        && !TrainFaults.blocked(level, context.group)
        && context.driver.schedule.nativeProgram(value, auto);
  }

  double navigate(DiscoveredPath path) {
    var context = context();
    if (context == null
        || path == null
        || path.destination == null
        || context.driver.active
        || context.driver.emergency
        || TrainFaults.blocked(level, context.group)
        || proxy.graph == null
        || proxy.graph.getPoint(
                com.simibubi.create.content.trains.graph.EdgePointType.STATION, path.destination.id)
            != path.destination
        || !level.dimension().equals(path.destination.getBlockEntityDimension())) return -1;
    var current = context.driver.schedule;
    if (path.destination.id.equals(current.nativeDestination())
        && current.running()
        && current.plan() != null
        && !current.routeRetry()
        && current.plan().network.live(current.plan().route))
      return Double.isFinite(current.remaining())
          ? Math.max(0, current.remaining())
          : current.plan().route.distance();
    AutomaticTrains.Plan route;
    try {
      route =
          AutomaticTrains.plan(
              level,
              context.group.units(),
              context.group.links(),
              context.driver.schedule,
              Set.of(),
              path.destination.id);
    } catch (RuntimeException error) {
      TrainFaults.fail(level, context.group, "native_navigation", error);
      return -1;
    }
    if (route == null) return -1;
    context.driver.schedule.nativeNavigate(route);
    return route.route.distance();
  }

  DiscoveredPath find(GlobalStation station, double maxCost) {
    var context = context();
    if (context == null
        || station == null
        || proxy.graph == null
        || Double.isNaN(maxCost)
        || maxCost < 0
        || !level.dimension().equals(station.getBlockEntityDimension())
        || proxy.graph.getPoint(
                com.simibubi.create.content.trains.graph.EdgePointType.STATION, station.id)
            != station
        || TrainFaults.blocked(level, context.group)) return null;
    try {
      var route =
          AutomaticTrains.plan(
              level,
              context.group.units(),
              context.group.links(),
              context.driver.schedule,
              Set.of(),
              station.id);
      if (route == null || route.route.distance() > maxCost) return null;
      var edges =
          new ArrayList<
              net.createmod.catnip.data.Couple<
                  com.simibubi.create.content.trains.graph.TrackNode>>();
      for (var edge : route.route.edges()) {
        var a = proxy.graph.getNode(edge.from());
        var b = proxy.graph.getNode(edge.to());
        if (a == null || b == null) return null;
        edges.add(net.createmod.catnip.data.Couple.create(a, b));
      }
      return new DiscoveredPath(route.route.distance(), route.route.distance(), edges, station);
    } catch (RuntimeException error) {
      return null;
    }
  }

  DiscoveredPath startInstruction(net.minecraft.world.level.Level requestedLevel) {
    var context = context();
    if (context == null
        || requestedLevel != level
        || context.driver.active
        || context.driver.emergency
        || TrainFaults.blocked(level, context.group)) return null;
    var c = context.driver.schedule;
    try {
      if (!c.running() && !c.nativeResume()) return null;
      if (!c.prepareInstructions()) return null;
      var route = c.plan();
      if (route == null) {
        route =
            AutomaticTrains.plan(
                level,
                context.group.units(),
                context.group.links(),
                c,
                Set.of(),
                c.nativeDestination());
        if (route == null) return null;
        c.acceptPlan(route);
      }
      var goal =
          proxy.graph == null
              ? null
              : proxy.graph.getPoint(
                  com.simibubi.create.content.trains.graph.EdgePointType.STATION,
                  route.route.goal().id());
      return goal == null ? null : find(goal, Double.MAX_VALUE);
    } catch (RuntimeException error) {
      TrainFaults.fail(level, context.group, "native_instruction", error);
      return null;
    }
  }

  void cancel() {
    var context = context();
    if (context != null) context.driver.schedule.nativeCancel();
  }

  void discard() {
    var context = context();
    if (context == null) return;
    if (context.driver.schedule.item().isEmpty()) context.driver.schedule.removeItem();
    else context.driver.schedule.nativeCancel();
  }

  void crash() {
    var context = context();
    if (context == null) return;
    context.driver.nativeSpeed = 0;
    context.driver.schedule.pause(ScheduleController.EMERGENCY);
    if (!context.driver.emergency) {
      emergencyOwned = true;
      emergencyButtonAt = context.driver.buttonAt;
    }
    DriverControls.emergency(context.driver);
    for (var u : context.group.units()) {
      u.command = null;
      if (!u.detached) VehiclePhysics.release(u, "native_crash");
    }
    proxy.derailed = derailed = true;
  }

  void detach() {
    var context = context();
    if (context == null) return;
    crash();
  }

  void reattach(net.minecraft.world.level.Level requestedLevel) {
    var context = context();
    if (context == null || requestedLevel != level) return;
    var samples = new HashMap<VehiclePhysics.Unit, TrackNetwork.Sample[]>();
    for (var u : context.group.units()) {
      if (SubLevelPhysicsSystem.get(level)
              .getPipeline()
              .getLinearVelocity(u.carrier, new Vector3d())
              .length()
          > .15) return;
      var heading = new Vector3d(0, 0, 1).rotate(u.bogie.logicalPose().orientation());
      var pair = new TrackNetwork.Sample[2];
      for (int end = 0; end < 2; end++) {
        pair[end] =
            TrackNetwork.nearest(
                level,
                VehiclePhysics.world(u.bogie, u.axle(end)),
                heading,
                null,
                .4,
                u.mount.gauge);
        if (pair[end] == null) return;
      }
      samples.put(u, pair);
    }
    for (var u : context.group.units()) {
      u.detached = false;
      u.samples[0] = samples.get(u)[0];
      u.samples[1] = samples.get(u)[1];
      var tag = u.bogie.getUserDataTag();
      if (tag != null) {
        tag = tag.copy();
        tag.putBoolean("GuidedDerailed", false);
        u.bogie.setUserDataTag(tag);
      }
      u.exposure.curveSeconds = u.exposure.impactSeconds = 0;
      u.derailGraceUntil = level.getGameTime() + 40;
    }
    if (emergencyOwned && emergencyButtonAt == context.driver.buttonAt) {
      DriverControls.resetEmergency(context.driver);
      emergencyOwned = false;
    }
    proxy.derailed = derailed = false;
  }

  void honk() {
    var context = context();
    if (context == null || level.getGameTime() < nextHonk) return;
    nextHonk = level.getGameTime() + 20;
    var point = proxy.mapPosition(level);
    if (point != null)
      com.simibubi.create.AllSoundEvents.WHISTLE_TRAIN_MANUAL.play(level, null, point, 1, 1);
  }

  boolean edit(Player player, String name, ResourceLocation icon, int color) {
    var context = context();
    if (context == null || icon == null || !TrainIconType.REGISTRY.containsKey(icon)) return false;
    if (player != null) {
      if (player.level() != level || !player.mayBuild()) return false;
      var station = proxy.getCurrentStation();
      boolean atStation =
          station != null
              && station.blockEntityPos != null
              && player.distanceToSqr(
                      net.minecraft.world.phys.Vec3.atCenterOf(station.blockEntityPos))
                  <= 64
              && level.mayInteract(player, station.blockEntityPos);
      if (!DriverControls.accessible(context.driver, player) && !atStation) return false;
      if (!level.mayInteract(player, context.driver.getBlockPos())) return false;
    }
    context.driver.nativeIdentity(name, icon, color);
    identity(context.driver);
    return true;
  }

  private void identity(DriverConsoleEntity driver) {
    proxy.name =
        driver.trainName().isEmpty()
            ? net.minecraft.network.chat.Component.translatable(
                "gui.physicalrailways.identity.name", proxy.id.toString().substring(0, 8))
            : UserText.userInput(driver.trainName());
    proxy.icon = TrainIconType.byId(driver.trainIcon);
    proxy.mapColorIndex = driver.trainMapColor;
    name = proxy.name.getString();
    icon = proxy.icon.getId();
    color = proxy.mapColorIndex;
  }

  void station(GlobalStation station) {
    var context = context();
    if (context == null) return;
    // Position and debounced presence are authoritative; a callback cannot fabricate arrival.
    if (station == null) StationPresence.releaseTrain(level, proxy);
    else if (StationPresence.confirmed(level, proxy, station)
        && Math.abs(context.driver.actual) < .15) proxy.station(station.id);
  }
}
