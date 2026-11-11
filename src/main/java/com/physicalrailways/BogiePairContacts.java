package com.physicalrailways;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.*;

/** Replaces only bogie/bogie voxel contact with one unilateral native contact plane. */
final class BogiePairContacts {
  private record Pair(UUID a, UUID b) {}

  private static final class Live {
    final VehiclePhysics.Unit a, b;
    final GenericConstraintHandle joint;
    Vector3d normal;
    final RecoveryWindow cleanup = new RecoveryWindow();

    Live(VehiclePhysics.Unit a, VehiclePhysics.Unit b, GenericConstraintHandle joint) {
      this.a = a;
      this.b = b;
      this.joint = joint;
    }

    void close() {
      if (joint.isValid()) joint.remove();
    }
  }

  private record Body(VehiclePhysics.Unit unit, Vector3d anchor, BogiePairContact.Pose pose) {}

  private static final Map<ServerLevel, Map<Pair, Live>> STATES = new WeakHashMap<>();

  static void step(
      SubLevelPhysicsSystem physics, Collection<VehiclePhysics.Unit> units, double dt) {
    var pipe = physics.getPipeline();
    var level = physics.getLevel();
    var live = STATES.computeIfAbsent(level, k -> new HashMap<>());
    var bodies = new ArrayList<Body>();
    for (var u : units) {
      try {
        if (u.ready(level.getGameTime()) && BodyGuard.readable(physics, u)) {
          var state = level.getBlockState(u.bogie.getPlot().getCenterBlock());
          if (!state.is(Content.BOGIE.get())) continue;
          var variant = BogieBlock.variant(state);
          var pose = pipe.readPose(u.bogie, new Pose3d());
          var anchor = new Vector3d(u.localCenter).add(BogiePairContact.offset(variant));
          bodies.add(
              new Body(
                  u,
                  anchor,
                  new BogiePairContact.Pose(
                      VehiclePhysics.world(u.bogie, anchor),
                      new Quaterniond(pose.orientation()),
                      !u.detached,
                      BogiePairContact.half(variant))));
        }
      } catch (RuntimeException error) {
        TrainFaults.fail(level, u, "contact_pose", error);
      }
    }
    bodies.sort(Comparator.comparing(b -> b.unit.bogie.getUniqueId()));
    var seen = new HashSet<Pair>();
    var bounds =
        bodies.stream()
            .map(
                b ->
                    new NearbyPairs.Bounds(
                        b.pose.center().x,
                        b.pose.center().y,
                        b.pose.center().z,
                        b.pose.half().length()))
            .toList();
    for (var pair : NearbyPairs.find(bounds, BogiePairContact.SKIN).pairs()) {
      try {
        var a = bodies.get(pair.a());
        var b = bodies.get(pair.b());
        var key = new Pair(a.unit.bogie.getUniqueId(), b.unit.bogie.getUniqueId());
        if (TrainFaults.blocked(level, a.unit.carrier.getUniqueId())
            || TrainFaults.blocked(level, b.unit.carrier.getUniqueId())) {
          if (live.containsKey(key)) seen.add(key);
          continue;
        }
        var old = live.get(key);
        if (old != null && (old.a != a.unit || old.b != b.unit || !old.joint.isValid())) {
          old.close();
          live.remove(key);
          old = null;
        }
        var contact = BogiePairContact.find(a.pose, b.pose, old == null ? null : old.normal);
        if (contact == null) continue;
        var frame = new Quaterniond().rotationTo(new Vector3d(1, 0, 0), contact.normal());
        var qa = new Quaterniond(a.pose.rotation()).conjugate().mul(frame);
        var qb = new Quaterniond(b.pose.rotation()).conjugate().mul(frame);
        if (old == null) {
          var joint =
              pipe.addConstraint(
                  a.unit.bogie,
                  b.unit.bogie,
                  new GenericConstraintConfiguration(
                      a.anchor, b.anchor, qa, qb, EnumSet.noneOf(ConstraintJointAxis.class)));
          try {
            joint.setLimit(ConstraintJointAxis.LINEAR_X, contact.limit(dt), 1_000_000);
            joint.setContactsEnabled(false);
            old = new Live(a.unit, b.unit, joint);
            live.put(key, old);
          } catch (RuntimeException error) {
            if (joint.isValid()) joint.remove();
            throw error;
          }
          org.slf4j.LoggerFactory.getLogger(PhysicalRailways.ID)
              .info(
                  "[BogiePairContact] begin bodies={}/{} gap={} normal={} mode=unilateral_native",
                  key.a,
                  key.b,
                  contact.gap(),
                  contact.normal());
        } else {
          old.joint.setFrame1(a.anchor, qa);
          old.joint.setFrame2(b.anchor, qb);
          old.joint.setLimit(ConstraintJointAxis.LINEAR_X, contact.limit(dt), 1_000_000);
        }
        old.normal = new Vector3d(contact.normal());
        seen.add(key);
      } catch (RuntimeException error) {
        TrainFaults.fail(level, bodies.get(pair.a()).unit, "bogey_contact", error);
        TrainFaults.fail(level, bodies.get(pair.b()).unit, "bogey_contact", error);
      }
    }
    live.entrySet()
        .removeIf(
            entry -> {
              if (seen.contains(entry.getKey())) return false;
              if (!entry.getValue().cleanup.probe(level.getGameTime())) return false;
              try {
                entry.getValue().close();
              } catch (RuntimeException error) {
                entry.getValue().cleanup.failed(level.getGameTime());
                TrainFaults.fail(level, entry.getValue().a, "contact_cleanup", error);
                TrainFaults.fail(level, entry.getValue().b, "contact_cleanup", error);
                return false;
              }
              org.slf4j.LoggerFactory.getLogger(PhysicalRailways.ID)
                  .info("[BogiePairContact] end bodies={}/{}", entry.getKey().a, entry.getKey().b);
              return true;
            });
  }

  static void unload(ServerLevel level) {
    var live = STATES.remove(level);
    if (live != null) live.values().forEach(Live::close);
  }
}
