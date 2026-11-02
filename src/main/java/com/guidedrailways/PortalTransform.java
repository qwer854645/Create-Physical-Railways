package com.guidedrailways;

import net.minecraft.world.phys.*;

/** Exact quarter-turn mapping; internal plot coordinates remain unchanged. */
record PortalTransform(Vec3 origin, Vec3 destination, int turns) {
  static boolean cooling(long now, long until, int duration) {
    return now >= 0 && until > now && until - now <= duration;
  }

  record Opening(Vec3 bottomCenter, boolean widthX, int width, int height) {
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
    if (!source.fits(group)) throw new IllegalArgumentException("message.guidedrailways.transfer.portal_fit");
    int turns = source.widthX == target.widthX ? 0 : source.widthX ? -1 : 1;
    var alignment = new PortalTransform(source.bottomCenter, target.bottomCenter, turns);
    var base = alignment.point(anchor);
    var rotation = new PortalTransform(anchor, Vec3.ZERO, turns);
    double speed = target.normal(rotation.vector(velocity));
    if (Math.abs(speed) < .01) {
      double direction = source.normal(anchor) <= source.normal(source.bottomCenter) ? 1 : -1;
      var normal = source.widthX ? new Vec3(0, 0, direction) : new Vec3(direction, 0, 0);
      speed = target.normal(rotation.vector(normal));
    }
    var destination = exit(rotation.bounds(group), !target.widthX,
        target.normal(target.bottomCenter), speed >= 0 ? 1 : -1, base);
    var result = new PortalTransform(anchor, destination, turns);
    if (!target.fits(result.bounds(group))) throw new IllegalArgumentException("message.guidedrailways.transfer.portal_fit");
    return result;
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
