package com.physicalrailways;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

/** Picking uses the same panel and moving-handle transforms as the block model and renderer. */
final class ConsoleInteraction {
  enum Part {
    NONE, LEVER, EMERGENCY, ROUTE_LEFT, ROUTE_DEFAULT, ROUTE_RIGHT;
    boolean route() { return this == ROUTE_LEFT || this == ROUTE_DEFAULT || this == ROUTE_RIGHT; }
    int command() { return this == ROUTE_LEFT ? 20 : this == ROUTE_RIGHT ? 21 : 22; }
  }
  private static final Vec3 CENTER = new Vec3(.5, 0, .5);
  private static final Vec3 PANEL = new Vec3(.5, .625, .5);
  private static final Vec3 HANDLE = new Vec3(11 / 16d, 12 / 16d, 7.5 / 16d);

  static Part pick(DriverConsoleEntity console, Player player) {
    Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(player.blockInteractionRange()));
    var pos = console.getBlockPos();
    if (!console.getLevel().isClientSide) {
      var hit = console.getLevel().clip(new net.minecraft.world.level.ClipContext(start, end,
          net.minecraft.world.level.ClipContext.Block.OUTLINE,
          net.minecraft.world.level.ClipContext.Fluid.NONE, player));
      if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(pos)) return Part.NONE;
    }
    var body = SableCompanion.INSTANCE.getContaining(console.getLevel(), pos);
    return pick(console, start, end, body == null ? null : body.logicalPose(), DriverModel.lever(console.notch), true);
  }

  static Part pick(DriverConsoleEntity console, Vec3 start, Vec3 end, Pose3dc pose, double lever, boolean animatedTolerance) {
    var pos = console.getBlockPos();
    // Sable can already redirect an interaction into plot coordinates. Never transform twice.
    if (pose != null && start.distanceToSqr(Vec3.atCenterOf(pos)) > 256) {
      var a = new Vector3d(start.x, start.y, start.z).sub(pose.position());
      var b = new Vector3d(end.x, end.y, end.z).sub(pose.position());
      pose.orientation().transformInverse(a); pose.orientation().transformInverse(b);
      a.add(pose.rotationPoint()); b.add(pose.rotationPoint());
      start = new Vec3(a.x, a.y, a.z); end = new Vec3(b.x, b.y, b.z);
    }
    var offset = Vec3.atLowerCornerOf(pos);
    start = start.subtract(offset); end = end.subtract(offset);
    var facing = console.getBlockState().getValue(DriverConsoleBlock.FACING);
    var result = pick(start, end, facing, lever, console.routeSelection.intent());
    // Client interpolation can still show an earlier handle angle. Accept only the handle's
    // physical sweep on the server, while the client picks its exact currently rendered angle.
    if (animatedTolerance && result == Part.NONE) for (int n = -DriverModel.BRAKE_NOTCHES; n <= DriverModel.MAX_NOTCH; n++)
      if (pick(start, end, facing, DriverModel.lever(n), console.routeSelection.intent()) == Part.LEVER) return Part.LEVER;
    return result;
  }

  static Part pick(Vec3 start, Vec3 end, Direction facing, double lever) {
    return pick(start, end, facing, lever, 0);
  }

  static Part pick(Vec3 start, Vec3 end, Direction facing, double lever, int selection) {
    double yaw = Math.toRadians((facing.toYRot() + 180) % 360);
    start = y(start, yaw); end = y(end, yaw);
    double solid = Math.min(hit(start, end, 1, 0, 1, 15, 3, 15), hit(start, end, 3, 3, 3, 13, 9, 13));
    Vec3 a = x(start, PANEL, -Math.PI / 8), b = x(end, PANEL, -Math.PI / 8);
    solid = Math.min(solid, hit(a, b, 1, 9, 2, 15, 11, 14));
    solid = Math.min(solid, hit(a, b, 3, 11.05, 4, 8, 11.4, 9));
    solid = Math.min(solid, hit(a, b, 10, 11, 5, 12, 12, 10));
    solid = Math.min(solid, hit(a, b, 3, 11, 10, 7, 12, 14));
    double button = hit(a, b, 3.35, 11.95, 10.35, 6.65, 13.15, 13.65);
    double route = Double.POSITIVE_INFINITY;
    Part routePart = Part.NONE;
    for (int i = -1; i <= 1; i++) {
      double left = 10 + i * 2;
      double distance = hit(a, b, left, 11.05, 10.9, left + 2, 11.8, 13.6);
      if (i == selection) distance = Math.min(distance,
          hit(a, b, left + .2, 11.6, 11.25, left + 1.8, 12.8, 13.05));
      if (distance < route) {
        route = distance;
        routePart = i < 0 ? Part.ROUTE_LEFT : i > 0 ? Part.ROUTE_RIGHT : Part.ROUTE_DEFAULT;
      }
    }
    a = x(a, HANDLE, Math.toRadians(36 * lever)); b = x(b, HANDLE, Math.toRadians(36 * lever));
    double handle = Math.min(hit(a, b, 10.35, 12, 6.85, 11.65, 14, 8.15),
        hit(a, b, 9.35, 13.85, 6.35, 12.65, 15.15, 8.65));
    double nearest = Math.min(route, Math.min(handle, button));
    return !Double.isFinite(nearest) || solid + 1e-7 < nearest ? Part.NONE
        : route <= Math.min(button, handle) ? routePart : button <= handle ? Part.EMERGENCY : Part.LEVER;
  }

  private static double hit(Vec3 a, Vec3 b, double x, double y, double z, double xx, double yy, double zz) {
    return new AABB(x / 16, y / 16, z / 16, xx / 16, yy / 16, zz / 16)
        .clip(a, b).map(p -> p.distanceToSqr(a)).orElse(Double.POSITIVE_INFINITY);
  }
  private static Vec3 y(Vec3 p, double angle) {
    p = p.subtract(CENTER); double c = Math.cos(angle), s = Math.sin(angle);
    return new Vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z).add(CENTER);
  }
  private static Vec3 x(Vec3 p, Vec3 pivot, double angle) {
    p = p.subtract(pivot); double c = Math.cos(angle), s = Math.sin(angle);
    return new Vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z).add(pivot);
  }
}
