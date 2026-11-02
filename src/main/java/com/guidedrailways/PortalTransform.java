package com.guidedrailways;

import net.minecraft.world.phys.*;

/** Exact quarter-turn mapping; internal plot coordinates remain unchanged. */
record PortalTransform(Vec3 origin, Vec3 destination, int turns) {
  static boolean cooling(long now, long until, int duration) {
    return now >= 0 && until > now && until - now <= duration;
  }

  record Opening(Vec3 bottomCenter, boolean widthX, int width, int height) {
    /** Projected containment is useful for diagnostics, never a transit precondition. */
    boolean fits(AABB box) {
      double low = widthX ? box.minX : box.minZ;
      double high = widthX ? box.maxX : box.maxZ;
      double center = widthX ? bottomCenter.x : bottomCenter.z;
      return low >= center - width / 2.0 - 1e-5 && high <= center + width / 2.0 + 1e-5
          && box.minY >= bottomCenter.y - 1e-5 && box.maxY <= bottomCenter.y + height + 1e-5;
    }
    double normal(Vec3 position) { return widthX ? position.z : position.x; }
  }

  static PortalTransform passage(Opening source, Opening target, AABB group, Vec3 anchor, Vec3 velocity) {
    int turns = source.widthX == target.widthX ? 0 : source.widthX ? -1 : 1;
    var rotation = new PortalTransform(anchor, Vec3.ZERO, turns);
    var local = rotation.bounds(group);
    // Like vanilla entity portal offsets: clamp within the available opening, center an oversized
    // body, and align its feet if it is taller than the opening. Actual destination collision is
    // checked separately; contact must not require the whole train to already be inside the frame.
    double sourceWidth = source.widthX ? group.getXsize() : group.getZsize();
    double sourceMin = source.widthX ? group.minX : group.minZ;
    double sourceEdge = (source.widthX ? source.bottomCenter.x : source.bottomCenter.z) - source.width / 2.0;
    double across = relative(sourceMin - sourceEdge, source.width - sourceWidth, .5);
    double up = relative(group.minY - source.bottomCenter.y, source.height - group.getYsize(), 0);
    // A quarter turn can reverse the width axis. Preserve the mirrored position in that case.
    var widthDirection = rotation.vector(source.widthX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1));
    if ((target.widthX ? widthDirection.x : widthDirection.z) < 0) across = 1 - across;
    double targetWidth = target.widthX ? local.getXsize() : local.getZsize();
    double widthSpace = target.width - targetWidth;
    double widthMin = (target.widthX ? target.bottomCenter.x : target.bottomCenter.z) - target.width / 2.0
        + widthSpace * (widthSpace > 0 ? across : .5);
    double dy = target.bottomCenter.y + .05 + Math.max(0, target.height - local.getYsize()) * up - local.minY;
    var base = target.widthX ? new Vec3(widthMin - local.minX, dy, 0) : new Vec3(0, dy, widthMin - local.minZ);
    double speed = target.normal(rotation.vector(velocity));
    if (Math.abs(speed) < .01) {
      double direction = source.normal(anchor) <= source.normal(source.bottomCenter) ? 1 : -1;
      var normal = source.widthX ? new Vec3(0, 0, direction) : new Vec3(direction, 0, 0);
      speed = target.normal(rotation.vector(normal));
    }
    var destination = exit(local, !target.widthX,
        target.normal(target.bottomCenter), speed >= 0 ? 1 : -1, base);
    return new PortalTransform(anchor, destination, turns);
  }

  private static double relative(double offset, double available, double fallback) {
    return available > 0 ? Math.clamp(offset / available, 0, 1) : fallback;
  }

  Vec3 vector(Vec3 value) {
    return switch (Math.floorMod(turns, 4)) {
      case 1 -> new Vec3(value.z, value.y, -value.x);
      case 2 -> new Vec3(-value.x, value.y, -value.z);
      case 3 -> new Vec3(-value.z, value.y, value.x);
      default -> value;
    };
  }

  Vec3 point(Vec3 value) { return vector(value.subtract(origin)).add(destination); }

  AABB bounds(AABB box) {
    var a = point(new Vec3(box.minX, box.minY, box.minZ));
    var b = point(new Vec3(box.maxX, box.maxY, box.maxZ));
    return new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
        Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
  }

  /** Place the whole group beyond the exit plane, retaining width/height offset at the entry. */
  static Vec3 exit(AABB local, boolean normalX, double plane, int direction, Vec3 base) {
    double near = normalX ? direction > 0 ? local.minX : local.maxX
        : direction > 0 ? local.minZ : local.maxZ;
    double coordinate = plane + direction * .75 - near;
    return normalX ? new Vec3(coordinate, base.y, base.z) : new Vec3(base.x, base.y, coordinate);
  }
}
