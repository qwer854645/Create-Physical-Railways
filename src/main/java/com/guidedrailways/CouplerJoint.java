package com.guidedrailways;

import static com.guidedrailways.CouplerCoordinates.*;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Owns one radial constraint and its mechanical lifecycle; no registry or player operations. */
final class CouplerJoint {
  final GenericConstraintHandle joint;
  final ServerSubLevel a, b;
  final BlockPos pa, pb;
  final CouplerEndpoint visualA, visualB;
  double ma, mb;
  long nextLog;
  boolean captured;

  CouplerJoint(
      GenericConstraintHandle joint,
      ServerSubLevel a,
      ServerSubLevel b,
      CouplerEndpoint ea,
      CouplerEndpoint eb) {
    this.joint = joint;
    this.a = a;
    this.b = b;
    this.pa = ea.getBlockPos().immutable();
    this.pb = eb.getBlockPos().immutable();
    visualA = ea;
    visualB = eb;
  }

  void close() {
    close(true);
  }

  void close(boolean notify) {
    if (joint.isValid()) joint.remove();
    if (notify) {
      visualA.visualPeer(null);
      visualB.visualPeer(null);
    }
  }

  static CouplerJoint create(
      UUID id, ServerSubLevel ba, ServerSubLevel bb, CouplerEndpoint a, CouplerEndpoint b) {
    var pipe = SubLevelPhysicsSystem.get(ba.getLevel()).getPipeline();
    var anchorA = blockOrigin(a).add(a.anchor);
    var anchorB = blockOrigin(b).add(b.anchor);
    var worldA = VehiclePhysics.world(ba, anchorA);
    var worldB = VehiclePhysics.world(bb, anchorB);
    var frame = CouplerGeometry.distanceFrame(worldA, worldB);
    double error = Math.abs(CouplerGeometry.extension(worldA, worldB, a.restLength));
    if (error > .35)
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[Coupler] restoring stretched buffer link={} extension={} with capped spring",
              id,
              error);
    GenericConstraintHandle joint = null;
    try {
      joint =
          pipe.addConstraint(
              ba,
              bb,
              new GenericConstraintConfiguration(
                  anchorA,
                  anchorB,
                  localFrame(ba, frame),
                  localFrame(bb, frame),
                  EnumSet.noneOf(ConstraintJointAxis.class)));
      joint.setContactsEnabled(true);
      var live = new CouplerJoint(joint, ba, bb, a, b);
      tune(live);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[CouplerJoint] created link={} bodies={}/{} nominalStroke={} savedAnchorError={} mode=standard_length_buffer",
              id,
              ba.getUniqueId(),
              bb.getUniqueId(),
              CouplerGeometry.HALF_STROKE * 2,
              error);
      return live;
    } catch (RuntimeException failure) {
      if (joint != null && joint.isValid()) joint.remove();
      throw failure;
    }
  }

  void update(UUID id) {
    var ba = a;
    var bb = b;
    var a = visualA;
    var b = visualB;
    var level = ba.getLevel();
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var kind = RailwayHardware.kind(a);
    double progress = Math.min(a.hardwareProgress(0), b.hardwareProgress(0));
    var anchorA = blockOrigin(a).add(a.anchor);
    var anchorB = blockOrigin(b).add(b.anchor);
    var worldA = VehiclePhysics.world(ba, anchorA);
    var worldB = VehiclePhysics.world(bb, anchorB);
    var frame = CouplerGeometry.distanceFrame(worldA, worldB);
    joint.setFrame1(anchorA, localFrame(ba, frame));
    joint.setFrame2(anchorB, localFrame(bb, frame));
    tune(this);
    if (!this.captured) {
      var delta = new Vector3d(worldB).sub(worldA);
      double distance = delta.length();
      double radialSpeed = Double.POSITIVE_INFINITY;
      if (distance > 1e-8) {
        var poseA = pipe.readPose(ba, new Pose3d());
        var poseB = pipe.readPose(bb, new Pose3d());
        var va =
            pipe.getAngularVelocity(ba, new Vector3d())
                .cross(new Vector3d(worldA).sub(poseA.position()))
                .add(pipe.getLinearVelocity(ba, new Vector3d()));
        var vb =
            pipe.getAngularVelocity(bb, new Vector3d())
                .cross(new Vector3d(worldB).sub(poseB.position()))
                .add(pipe.getLinearVelocity(bb, new Vector3d()));
        radialSpeed = vb.sub(va).dot(delta.div(distance));
      }
      if ((!CouplerMechanism.profile(kind).tensionOnly() || progress >= 1)
          && CouplerMechanism.canCapture(kind, distance, radialSpeed, a.restLength)) {
        this.joint.setLimit(
            ConstraintJointAxis.LINEAR_X,
            CouplerMechanism.lower(kind, a.restLength),
            CouplerMechanism.upper(kind, a.restLength));
        this.captured = true;
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .info(
                "[Coupler] buffer captured link={} distance={} stroke={}",
                id,
                distance,
                CouplerGeometry.HALF_STROKE * 2);
      }
    }
    a.visualPeer(b);
    b.visualPeer(a);
    if (TrainConfig.DETAILED_LOGGING.get() && level.getGameTime() >= this.nextLog) {
      this.nextLog = level.getGameTime() + 20;
      var wa = VehiclePhysics.world(ba, blockOrigin(a).add(a.anchor));
      var wb = VehiclePhysics.world(bb, blockOrigin(b).add(b.anchor));
      if (level.players().stream().anyMatch(p -> p.distanceToSqr(wa.x, wa.y, wa.z) < 4096)) {
        var frameWorld = new Quaterniond(frame);
        var delta = new Vector3d(wb).sub(wa).rotate(frameWorld.conjugate());
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
            .info(
                "[CouplerState] link={} bufferX={} transverseError={} supportedMassA={} supportedMassB={} joint={} socketDistance={} restLength={} swivelDeg={}",
                id,
                delta.x - a.restLength,
                Math.hypot(delta.y, delta.z),
                this.ma,
                this.mb,
                this.joint.isValid(),
                wa.distance(wb),
                a.restLength,
                Math.toDegrees(Math.acos(Math.clamp(-facing(ba, a).dot(facing(bb, b)), -1, 1))));
      }
    }
  }

  private static void tune(CouplerJoint live) {
    double ma = VehiclePhysics.coupledMass(live.a), mb = VehiclePhysics.coupledMass(live.b);
    var kind = RailwayHardware.kind(live.visualA);
    var profile = CouplerMechanism.profile(kind);
    if (!profile.tensionOnly() && Math.abs(live.ma - ma) < .01 && Math.abs(live.mb - mb) < .01)
      return;
    var spring = CouplerGeometry.spring(ma, mb);
    double progress = Math.min(live.visualA.hardwareProgress(0), live.visualB.hardwareProgress(0));
    double target = CouplerMechanism.workingLength(kind, live.visualA.restLength, progress);
    double distance =
        VehiclePhysics.world(live.a, blockOrigin(live.visualA).add(live.visualA.anchor))
            .distance(
                VehiclePhysics.world(live.b, blockOrigin(live.visualB).add(live.visualB.anchor)));
    boolean slack = profile.tensionOnly() && distance <= target;
    double damping = spring.damping();
    if (profile.tensionOnly() && !slack) {
      var pipe = SubLevelPhysicsSystem.get(live.a.getLevel()).getPipeline();
      var wa = VehiclePhysics.world(live.a, blockOrigin(live.visualA).add(live.visualA.anchor));
      var wb = VehiclePhysics.world(live.b, blockOrigin(live.visualB).add(live.visualB.anchor));
      var axis = new Vector3d(wb).sub(wa);
      if (axis.lengthSquared() > 1e-12) {
        var pa = pipe.readPose(live.a, new Pose3d());
        var pb = pipe.readPose(live.b, new Pose3d());
        var va =
            pipe.getAngularVelocity(live.a, new Vector3d())
                .cross(new Vector3d(wa).sub(pa.position()))
                .add(pipe.getLinearVelocity(live.a, new Vector3d()));
        var vb =
            pipe.getAngularVelocity(live.b, new Vector3d())
                .cross(new Vector3d(wb).sub(pb.position()))
                .add(pipe.getLinearVelocity(live.b, new Vector3d()));
        double radial = vb.sub(va).dot(axis.normalize());
        damping =
            CouplerMechanism.tensionDamping(distance - target, radial, spring.stiffness(), damping);
      }
    }
    live.joint.setMotor(
        ConstraintJointAxis.LINEAR_X,
        target,
        slack ? 0 : spring.stiffness(),
        slack ? 0 : damping,
        true,
        slack ? 0 : spring.maxForce());
    live.ma = ma;
    live.mb = mb;
  }
}
