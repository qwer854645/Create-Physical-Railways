package com.guidedrailways;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Independently written guided-body prototype. No per-wheel bodies or patched native library. */
public final class VehiclePhysics {
  /** Mount plate underside, flush with the top of its block so blocks above sit on it. */
  private static final double PLATE_BOTTOM = BogieGeometry.MOUNT_UNDERSIDE;

  private static final Quaterniond ANCHOR_ROT = new Quaterniond().rotateY(-Math.PI / 2);
  private static final Map<ServerLevel, Map<BlockPos, MountEntity>> INPUTS = new WeakHashMap<>();
  private static final Map<ServerLevel, Map<UUID, Unit>> ACTIVE = new WeakHashMap<>();

  static final class Unit {
    final MountEntity mount;
    final ServerSubLevel carrier, bogie;
    final Vector3d localCenter;
    DriverConsoleEntity driver;
    TrainCommand command;
    GenericConstraintHandle pivot;
    final GenericConstraintHandle[] guides = new GenericConstraintHandle[2];
    final TrackNetwork.Sample[] samples = new TrackNetwork.Sample[2];
    final DerailmentModel.Exposure exposure = new DerailmentModel.Exposure();
    double mass, tractionMass, commandedForce, speed, curveRisk, lateralSpeed, electricWatts;
    long lastSeen, nextLog, derailGraceUntil;
    boolean detached, reverseTravel, trainPowered, electricEquipped;
    int supports = 1;

    Unit(MountEntity mount, ServerSubLevel carrier, ServerSubLevel bogie) {
      var variant =
          BogieBlock.variant(bogie.getLevel().getBlockState(bogie.getPlot().getCenterBlock()));
      if (mount.variant != variant) {
        mount.variant = variant;
        mount.setChanged();
      }
      MountBlock.updateStyle(mount);
      this.mount = mount;
      this.carrier = carrier;
      this.bogie = bogie;
      localCenter = center(bogie);
      var tag = bogie.getUserDataTag();
      detached = tag != null && tag.getBoolean("GuidedDerailed");
    }

    Vector3d axle(int i) {
      return new Vector3d(localCenter)
          .add(0, -mount.variant.ride, i == 0 ? mount.variant.half : -mount.variant.half);
    }

    void clearGuides() {
      for (int i = 0; i < 2; i++) {
        if (guides[i] != null && guides[i].isValid()) guides[i].remove();
        guides[i] = null;
      }
    }

    void close() {
      clearGuides();
      if (pivot != null && pivot.isValid()) pivot.remove();
    }
  }

  private VehiclePhysics() {}

  static Collection<MountEntity> mounts(ServerLevel level) {
    return List.copyOf(INPUTS.getOrDefault(level, Map.of()).values());
  }

  static Unit unit(ServerLevel level, UUID id) {
    return ACTIVE.getOrDefault(level, Map.of()).get(id);
  }

  static Collection<Unit> cargoUnits(ServerLevel level) {
    return List.copyOf(ACTIVE.getOrDefault(level, Map.of()).values());
  }

  static Vector3d center(ServerSubLevel body) {
    var p = body.getPlot().getCenterBlock();
    return new Vector3d(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
  }

  static Vector3d world(ServerSubLevel body, Vector3dc local) {
    var pose =
        SubLevelPhysicsSystem.get(body.getLevel()).getPipeline().readPose(body, new Pose3d());
    return new Vector3d(local)
        .sub(body.logicalPose().rotationPoint())
        .rotate(pose.orientation())
        .add(pose.position());
  }

  static double coupledMass(ServerSubLevel carrier) {
    double mass = carrier.getMassTracker().getMass();
    for (var u : ACTIVE.getOrDefault(carrier.getLevel(), Map.of()).values())
      if (u.carrier == carrier && !u.bogie.isRemoved()) mass += u.bogie.getMassTracker().getMass();
    return mass;
  }

  static boolean hasVehicles(ServerLevel level) {
    return !INPUTS.getOrDefault(level, Map.of()).isEmpty()
        || !ACTIVE.getOrDefault(level, Map.of()).isEmpty();
  }

  static void register(MountEntity mount) {
    if (mount.getLevel() instanceof ServerLevel level && mount.bogieId != null)
      INPUTS
          .computeIfAbsent(level, l -> new HashMap<>())
          .put(mount.getBlockPos().immutable(), mount);
  }

  static void tick(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    var system = SubLevelPhysicsSystem.get(level);
    if (container == null || system == null) return;
    var inputs = INPUTS.computeIfAbsent(level, l -> new HashMap<>());
    inputs
        .values()
        .removeIf(
            m -> m.isRemoved() || m.bogieId == null || level.getBlockEntity(m.getBlockPos()) != m);
    var units = ACTIVE.computeIfAbsent(level, l -> new HashMap<>());
    for (var mount : inputs.values()) {
      if (!(SableCompanion.INSTANCE.getContaining(level, mount.getBlockPos())
              instanceof ServerSubLevel carrier)
          || carrier.isRemoved()) continue;
      if (!(container.getSubLevel(mount.bogieId) instanceof ServerSubLevel bogie)
          || bogie.isRemoved()
          || bogie == carrier) continue;
      if (!CollisionAssembler.populate(bogie, true)) continue;
      var unit = units.get(mount.bogieId);
      if (unit == null || unit.carrier != carrier || unit.mount != mount) {
        if (unit != null) unit.close();
        unit = new Unit(mount, carrier, bogie);
        connectPivot(unit);
        units.put(mount.bogieId, unit);
      }
      unit.lastSeen = level.getGameTime();
    }
    units
        .entrySet()
        .removeIf(
            e -> {
              var u = e.getValue();
              if (u.carrier.isRemoved()
                  || u.bogie.isRemoved()
                  || level.getGameTime() - u.lastSeen > 40) {
                u.close();
                return true;
              }
              return false;
            });
    TrainConsists.refresh(level, units.values());
    TrainConsists.applyChanges(level);
    TrainElectricity.refresh(level);
    DriverControls.refresh(level, units.values());
    PerfCounters.report(level, units.size());
    var counts = new HashMap<UUID, Integer>();
    for (var u : units.values()) counts.merge(u.carrier.getUniqueId(), 1, Integer::sum);
    for (var u : units.values()) {
      int supports = counts.get(u.carrier.getUniqueId());
      if (u.supports != supports) {
        if (u.pivot != null && u.pivot.isValid()) u.pivot.remove();
        u.supports = supports;
        connectPivot(u);
      }
      u.mass = u.carrier.getMassTracker().getMass() / supports + u.bogie.getMassTracker().getMass();
      u.mount.setLoad(u.mass);
    }
    VehicleTraction.allocate(level);
    for (var u : units.values()) {
      if (level.getBlockEntity(u.bogie.getPlot().getCenterBlock()) instanceof BogieEntity visual) {
        visual.speed = u.speed;
        visual.distance += u.speed / 20;
        if (visual.motionSync.update(level.getGameTime(), visual.speed, visual.distance))
          visual.sendData();
      }
      if (level.getGameTime() >= u.nextLog) {
        u.nextLog = level.getGameTime() + 20;
        var pos = world(u.bogie, u.localCenter);
        if (level.players().stream().anyMatch(p -> p.distanceToSqr(pos.x, pos.y, pos.z) < 4096)) {
          var pipeline = system.getPipeline();
          double verticalSpeed = pipeline.getLinearVelocity(u.bogie, new Vector3d()).y,
              angularSpeed = pipeline.getAngularVelocity(u.bogie, new Vector3d()).length();
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
              .info(
                  "[GuidedBogie] body={} speed={} target={} inputRpm={} supportedMass={} tractionMass={} commandedForce={} brakes={} guided={} travel={} verticalSpeed={} angularSpeedRad={} curveRisk={} lateralSpeed={} carrier={} mounts={} pivot={} anchors={}",
                  u.bogie.getUniqueId(),
                  u.speed,
                  u.driver == null
                      ? u.mount.getSpeed() * TrainConfig.speedPerRpm()
                      : VehicleTraction.requestedSpeed(u),
                  u.mount.getSpeed(),
                  u.mass,
                  u.tractionMass,
                  u.commandedForce,
                  u.mount.braking
                      || (u.command != null
                          ? u.command.hold() || u.command.emergency() || u.command.notch() < 0
                          : u.driver != null
                              && (!u.driver.enabled
                                  || u.driver.emergency
                                  || u.driver.notch < 0
                                  || u.driver.powered == 0)),
                  !u.detached,
                  u.reverseTravel ? "reverse" : "forward",
                  verticalSpeed,
                  angularSpeed,
                  u.curveRisk,
                  u.lateralSpeed,
                  u.carrier.getUniqueId(),
                  counts.get(u.carrier.getUniqueId()),
                  TrainConfig.DETAILED_LOGGING.get() ? pivotSummary(u) : "off",
                  TrainConfig.DETAILED_LOGGING.get() ? anchorSummary(u) : "off");
        }
      }
    }
  }

  private static String pivotSummary(Unit u) {
    var pipe = SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
    var upper =
        new Vector3d(
            u.mount.getBlockPos().getX() + .5,
            u.mount.getBlockPos().getY() + PLATE_BOTTOM,
            u.mount.getBlockPos().getZ() + .5);
    var lower = new Vector3d(u.localCenter).add(0, u.mount.pivotHeight(), 0);
    var a = pipe.readPose(u.carrier, new Pose3d());
    var b = pipe.readPose(u.bogie, new Pose3d());
    var relative =
        new Quaterniond(b.orientation())
            .conjugate()
            .mul(a.orientation())
            .mul(u.mount.pivotReference);
    var rate =
        pipe.getAngularVelocity(u.carrier, new Vector3d())
            .sub(pipe.getAngularVelocity(u.bogie, new Vector3d()))
            .rotate(new Quaterniond(b.orientation()).conjugate());
    var up = new Vector3d(0, 1, 0).rotate(relative);
    double tilt = Math.toDegrees(Math.acos(Math.clamp(up.y, -1, 1)));
    var neutral = new Quaterniond(a.orientation()).mul(u.mount.pivotReference);
    var yawForward = new Vector3d(0, 0, 1).rotate(b.orientation()).rotate(neutral.conjugate());
    double yawError = Math.toDegrees(Math.atan2(yawForward.x, yawForward.z));
    return String.format(
        java.util.Locale.ROOT,
        "anchorError=%.5f,tiltDeg=%.3f,yawErrorDeg=%.3f,yawRateDeg=%.3f,pitchRateDeg=%.3f,rollRateDeg=%.3f,mode=%s",
        world(u.carrier, upper).distance(world(u.bogie, lower)),
        tilt,
        yawError,
        Math.toDegrees(rate.y),
        Math.toDegrees(rate.x),
        Math.toDegrees(rate.z),
        u.supports == 1 ? "stable" : "articulated");
  }

  private static String anchorSummary(Unit u) {
    var text = new StringBuilder();
    var pipe = SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
    var forward = new Vector3d(0, 0, 1).rotate(pipe.readPose(u.bogie, new Pose3d()).orientation());
    for (int i = 0; i < 2; i++) {
      if (i > 0) text.append(';');
      var s = u.samples[i];
      if (s == null) {
        text.append("missing");
        continue;
      }
      text.append(
          String.format(
              java.util.Locale.ROOT,
              "%s:%d-%d,t=%.4f,curve=%s,error=%.5f,align=%.4f,joint=%s",
              i == 0 ? "front" : "rear",
              s.path().a(),
              s.path().b(),
              s.t(),
              s.path().edge().isTurn(),
              world(u.bogie, u.axle(i)).distance(s.position()),
              forward.dot(s.tangent()),
              u.guides[i] != null && u.guides[i].isValid()));
    }
    return text.toString();
  }

  private static void connectPivot(Unit u) {
    var pipe = SubLevelPhysicsSystem.get(u.bogie.getLevel()).getPipeline();
    var upper =
        new Vector3d(
            u.mount.getBlockPos().getX() + .5,
            u.mount.getBlockPos().getY() + PLATE_BOTTOM,
            u.mount.getBlockPos().getZ() + .5);
    var lower = new Vector3d(u.localCenter).add(0, u.mount.pivotHeight(), 0);
    var locks =
        EnumSet.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_Z);
    if (u.supports == 1) locks.add(ConstraintJointAxis.ANGULAR_X);
    if (!u.mount.pivotReferenceKnown) {
      var carrier = pipe.readPose(u.carrier, new Pose3d());
      var bogie = pipe.readPose(u.bogie, new Pose3d());
      u.mount.pivotReference.set(
          BogieYawModel.reference(carrier.orientation(), bogie.orientation()));
      u.mount.pivotReferenceKnown = true;
      u.mount.setChanged();
    }
    u.pivot =
        pipe.addConstraint(
            u.carrier,
            u.bogie,
            new GenericConstraintConfiguration(
                upper, lower, u.mount.pivotReference, new Quaterniond(), locks));
    u.pivot.setContactsEnabled(false);
    // A stable bearing replaces the prototype's spring-supported empty plate.
    // Weak yaw centering, with damping and a small torque cap; yaw remains unlocked.
    var spring = BogieYawModel.spring(u.supports);
    u.pivot.setMotor(
        ConstraintJointAxis.ANGULAR_Y,
        0,
        spring.stiffness(),
        spring.damping(),
        true,
        spring.maxTorque());
    if (u.supports > 1) {
      // Bogies at opposite ends of a long car may meet different grades.
      u.pivot.setLimit(ConstraintJointAxis.ANGULAR_X, -.35, .35);
      u.pivot.setMotor(ConstraintJointAxis.ANGULAR_X, 0, 0, 12, true, 4000);
    }
  }

  static void step(SubLevelPhysicsSystem physics, double dt) {
    if (!TrainConfig.PERFORMANCE_LOGGING.get()) {
      stepVehicle(physics, dt);
      return;
    }
    long start = System.nanoTime();
    try {
      stepVehicle(physics, dt);
    } finally {
      PerfCounters.step(physics.getLevel(), System.nanoTime() - start);
    }
  }

  private static void stepVehicle(SubLevelPhysicsSystem physics, double dt) {
    if (!(dt > 0 && Double.isFinite(dt))) return;
    var level = physics.getLevel();
    var pipe = physics.getPipeline();
    BogiePairContacts.step(physics, ACTIVE.getOrDefault(level, Map.of()).values(), dt);
    for (var plan : ConsistRoutes.prepare(level, ACTIVE.getOrDefault(level, Map.of()).values())) {
      var u = plan.unit();
      if (u.carrier.isRemoved()
          || u.bogie.isRemoved()
          || u.mount.isRemoved()
          || u.lastSeen + 2 < level.getGameTime()) continue;
      var pose = pipe.readPose(u.bogie, new Pose3d());
      var heading = new Vector3d(0, 0, 1).rotate(pose.orientation());
      if (u.detached) {
        u.speed = pipe.getLinearVelocity(u.bogie, new Vector3d()).dot(heading);
        continue;
      }
      // Leading axle steers at junctions (Create TravellingPoint.steer); trailing axle follows it.
      u.reverseTravel =
          BranchSelect.reverseTravel(
              pipe.getLinearVelocity(u.bogie, new Vector3d()).dot(heading),
              VehicleTraction.requestedSpeed(u),
              u.reverseTravel);
      boolean reverse = u.reverseTravel;
      int lead = reverse ? 1 : 0, trail = 1 - lead;
      double approachSpeed = Math.abs(pipe.getLinearVelocity(u.bogie, new Vector3d()).dot(heading));
      double nose =
          PhysicalSignals.nose(
                  u, world(u.bogie, u.axle(lead)), new Vector3d(heading).mul(reverse ? -1 : 1))
              + .25;
      double switchRoom =
          TrackNetwork.switchDistance(
              level,
              u.samples[lead],
              heading,
              reverse,
              plan.axle(lead),
              approachSpeed * approachSpeed / (2 * TrainConfig.BRAKING.get()) + nose + 8,
              nose);
      switchRoom = plan.axle(lead).limit(switchRoom);
      var atLead = world(u.bogie, u.axle(lead));
      u.samples[lead] =
          TrackNetwork.follow(
              level, atLead, heading, u.samples[lead], null, .8, reverse, plan.axle(lead));
      plan.axle(lead).followed(u.samples[lead]);
      if (u.samples[lead] == null) {
        release(u, "track_missing_or_out_of_range");
        continue;
      }
      var atTrail = world(u.bogie, u.axle(trail));
      u.samples[trail] =
          TrackNetwork.follow(
              level,
              atTrail,
              heading,
              u.samples[trail],
              u.samples[lead],
              .8,
              reverse,
              plan.axle(trail));
      if (u.samples[trail] == null) {
        release(u, "track_missing_or_out_of_range");
        continue;
      }
      if (TrainConfig.DERAILMENT.get()
          && level.getGameTime() >= u.derailGraceUntil
          && checkDerailment(u, physics, dt)) continue;
      u.speed = 0;
      u.commandedForce = 0;
      for (int i = 0; i < 2; i++) {
        var sample = u.samples[i];
        var q = sample.orientation();
        if (u.guides[i] == null || !u.guides[i].isValid()) {
          // Two point guides already constrain pitch/yaw. A single roll lock is sufficient.
          var locks = EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z);
          if (i == 0) locks.add(ConstraintJointAxis.ANGULAR_X);
          u.guides[i] =
              pipe.addConstraint(
                  null,
                  u.bogie,
                  new GenericConstraintConfiguration(
                      sample.position(), u.axle(i), q, ANCHOR_ROT, locks));
          u.guides[i].setContactsEnabled(false);
        } else u.guides[i].setFrame1(sample.position(), q);
        // Longitudinal translation is not locked: the solver never sets the vehicle's forward
        // position.
        double speed = pipe.getLinearVelocity(u.bogie, new Vector3d()).dot(sample.tangent());
        u.speed += speed * .5;
        double force = VehicleTraction.force(u, speed, dt);
        force =
            RailwayStopModel.force(speed, force, switchRoom, u.mass, TrainConfig.BRAKING.get(), dt);
        u.commandedForce += force * .5;
        var impulse =
            new Vector3d(sample.tangent())
                .mul(force * dt * .5)
                .rotate(new Quaterniond(pose.orientation()).conjugate());
        // Apply traction at the frame centre height, with front/rear positions distributed along
        // its length.
        var point =
            new Vector3d(u.localCenter)
                .add(0, 0, i == 0 ? u.mount.variant.half : -u.mount.variant.half);
        pipe.applyImpulse(u.bogie, point, impulse);
      }
    }
  }

  static void consoleRemoved(DriverConsoleEntity console) {
    for (var u : ACTIVE.getOrDefault(console.getLevel(), Map.of()).values())
      if (u.driver == console
          && !u.carrier.isRemoved()
          && !u.mount.isRemoved()
          && console.getLevel().hasChunkAt(u.mount.getBlockPos())
          && console.getLevel().getBlockEntity(u.mount.getBlockPos()) == u.mount) {
        u.mount.braking = true;
        u.mount.setChanged();
        u.mount.sendData();
      }
  }

  static void releaseMountBrakes(DriverConsoleEntity console) {
    for (var u : ACTIVE.getOrDefault(console.getLevel(), Map.of()).values())
      if (u.driver == console || u.command != null && u.command.owner() == console) {
        u.mount.braking = false;
        u.mount.setChanged();
        u.mount.sendData();
      }
  }

  private static void release(Unit u, String reason) {
    // Keep current velocity and the carrier bearing; remove only world-to-track constraints.
    var anchors = anchorSummary(u);
    u.clearGuides();
    u.detached = true;
    var tag = u.bogie.getUserDataTag();
    if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
    tag.putBoolean("GuidedDerailed", true);
    u.bogie.setUserDataTag(tag);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[GuideReleased] body={} reason={} speed={} curveRisk={} lateralSpeed={} curveSeconds={} impactSeconds={} anchors={}",
            u.bogie.getUniqueId(),
            reason,
            u.speed,
            u.curveRisk,
            u.lateralSpeed,
            u.exposure.curveSeconds,
            u.exposure.impactSeconds,
            anchors);
  }

  private static boolean checkDerailment(Unit u, SubLevelPhysicsSystem physics, double dt) {
    var pipe = physics.getPipeline();
    var velocity = pipe.getLinearVelocity(u.bogie, new Vector3d());
    var angular = pipe.getAngularVelocity(u.bogie, new Vector3d());
    var bogieCom = world(u.bogie, u.bogie.logicalPose().rotationPoint());
    var carrierCom = world(u.carrier, u.carrier.logicalPose().rotationPoint());
    double lowerMass = u.bogie.getMassTracker().getMass(),
        upperMass = u.carrier.getMassTracker().getMass() / u.supports;
    var supportedCom =
        new Vector3d(bogieCom)
            .mul(lowerMass)
            .fma(upperMass, carrierCom)
            .div(Math.max(.001, lowerMass + upperMass));
    u.curveRisk = 0;
    u.lateralSpeed = 0;
    u.speed = 0;
    for (int i = 0; i < 2; i++) {
      var sample = u.samples[i];
      double speed = velocity.dot(sample.tangent());
      u.speed += speed * .5;
      double height = new Vector3d(supportedCom).sub(sample.position()).dot(sample.up());
      u.curveRisk =
          Math.max(
              u.curveRisk,
              DerailmentModel.curveRisk(
                  speed, sample.lateralCurvature(), height, TrainConfig.DERAIL_LATERAL.get()));
      // Use axle point velocity, including yaw, so normal turning is not a side impact.
      var lever = world(u.bogie, u.axle(i)).sub(bogieCom);
      var pointVelocity = new Vector3d(angular).cross(lever).add(velocity);
      var side = new Vector3d(sample.tangent()).cross(sample.up()).normalize();
      u.lateralSpeed = Math.max(u.lateralSpeed, Math.abs(pointVelocity.dot(side)));
    }
    var reason =
        u.exposure.update(
            u.curveRisk,
            u.lateralSpeed,
            TrainConfig.DERAIL_IMPACT.get(),
            TrainConfig.DERAIL_DELAY.get(),
            dt);
    if (reason == null) return false;
    release(u, reason);
    return true;
  }

  static Unit owner(Level level, BlockPos pos) {
    if (!(level instanceof ServerLevel server)) return null;
    var units = ACTIVE.getOrDefault(server, Map.of());
    if (level.getBlockEntity(pos) instanceof MountEntity mount && mount.bogieId != null)
      return units.get(mount.bogieId);
    if (SableCompanion.INSTANCE.getContaining(level, pos) instanceof ServerSubLevel b)
      return units.get(b.getUniqueId());
    return null;
  }

  static void dismantle(Unit u, Player player) {
    var level = u.bogie.getLevel();
    u.close();
    ACTIVE.getOrDefault(level, Map.of()).remove(u.bogie.getUniqueId());
    var container = SubLevelContainer.getContainer(level);
    container.removeSubLevel(u.bogie, SubLevelRemovalReason.REMOVED);
    u.mount.bogieId = null;
    level.setBlock(u.mount.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    // Removing the final mount can already remove the carrier's native body.
    if (!u.carrier.isRemoved()) {
      u.carrier.buildMassTracker();
      u.carrier.updateMergedMassData(1);
      if (u.carrier.getMassTracker().getMass() <= .001) {
        if (!u.carrier.isRemoved())
          container.removeSubLevel(u.carrier, SubLevelRemovalReason.REMOVED);
      } else if (!u.carrier.isRemoved())
        SubLevelPhysicsSystem.get(level).getPipeline().onStatsChanged(u.carrier);
    }
    if (!player.getAbilities().instabuild) {
      var stack = Content.bogieItem();
      if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
  }

  static void unload(ServerLevel level) {
    VehiclePlacement.unload(level);
    BogiePairContacts.unload(level);
    var units = ACTIVE.remove(level);
    if (units != null) {
      units.values().forEach(Unit::close);
    }
    INPUTS.remove(level);
    MountBlock.clear(level);
    PerfCounters.unload(level);
    ConsistRoutes.unload(level);
  }
}
