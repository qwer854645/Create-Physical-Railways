package com.guidedrailways;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Coordinate conversion shared by link validation, joint mechanics and plot transfer. */
final class CouplerCoordinates {
  static boolean loaded(ServerLevel level, CouplerEndpoint e) {
    return e != null
        && !e.isRemoved()
        && level.hasChunkAt(e.getBlockPos())
        && level.getBlockEntity(e.getBlockPos()) == e.owner;
  }

  static ServerSubLevel body(ServerLevel level, CouplerEndpoint e) {
    return SableCompanion.INSTANCE.getContaining(level, e.getBlockPos()) instanceof ServerSubLevel b
            && !b.isRemoved()
        ? b
        : null;
  }

  static Vector3d blockOrigin(CouplerEndpoint e) {
    var p = e.getBlockPos();
    return new Vector3d(p.getX(), p.getY(), p.getZ());
  }

  static Vector3d socket(CouplerEndpoint e) {
    return blockOrigin(e).add(RailwayHardware.pivot(e));
  }

  static Vector3d head(CouplerEndpoint e) {
    return blockOrigin(e).add(RailwayHardware.tip(e));
  }

  static Vector3d facing(ServerSubLevel b, CouplerEndpoint e) {
    var f = e.getBlockState().getValue(CouplerBlock.FACING);
    return new Vector3d(f.getStepX(), 0, f.getStepZ())
        .rotate(
            SubLevelPhysicsSystem.get(b.getLevel())
                .getPipeline()
                .readPose(b, new Pose3d())
                .orientation());
  }

  static Vector3d up(ServerSubLevel b) {
    return new Vector3d(0, 1, 0)
        .rotate(
            SubLevelPhysicsSystem.get(b.getLevel())
                .getPipeline()
                .readPose(b, new Pose3d())
                .orientation());
  }

  static Vector3d local(ServerSubLevel b, Vector3d world) {
    var pose = SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b, new Pose3d());
    return new Vector3d(world)
        .sub(pose.position())
        .rotate(new Quaterniond(pose.orientation()).conjugate())
        .add(b.logicalPose().rotationPoint());
  }

  static Quaterniond localFrame(ServerSubLevel b, Quaterniond world) {
    var pose = SubLevelPhysicsSystem.get(b.getLevel()).getPipeline().readPose(b, new Pose3d());
    return new Quaterniond(pose.orientation()).conjugate().mul(world);
  }

  private CouplerCoordinates() {}
}
