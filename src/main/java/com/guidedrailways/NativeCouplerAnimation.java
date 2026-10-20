package com.guidedrailways;

import java.util.*;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Canonical native-model transforms shared by rendering and living-entity collision. */
final class NativeCouplerAnimation {
  enum Part {
    BODY,
    MOUNT,
    PIN,
    LINK,
    JAW,
    RIGGING,
    SCREW
  }

  private NativeCouplerAnimation() {}

  static Part classify(CouplerKind kind, String group, Vector3d from, Vector3d to, boolean single) {
    if (kind == CouplerKind.LINK || kind == CouplerKind.LINKLESS) {
      if (to.x - from.x < 1.1 && to.z - from.z < 1.1 && to.y - from.y >= 3) return Part.PIN;
      if (group.equals("link") || group.equals("pin")) return Part.LINK;
    } else if (kind == CouplerKind.KNUCKLE || kind == CouplerKind.KNUCKLE_SPLIT) {
      if (group.equals("knuckle")) return Part.JAW;
      if (from.z >= (single ? 12 : 9)) return Part.MOUNT;
    } else if (kind == CouplerKind.SCREWLINK) {
      if (group.equals("screw")) return Part.SCREW;
      if (group.equals("flat_links") || group.equals("shackle")) return Part.RIGGING;
    }
    return Part.BODY;
  }

  static void groups(com.google.gson.JsonArray groups, Map<Integer, String> names) {
    if (groups == null) return;
    for (var value : groups)
      if (value.isJsonObject()) {
        var group = value.getAsJsonObject();
        var children = group.getAsJsonArray("children");
        if (children == null) continue;
        for (var child : children)
          if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber())
            names.put(child.getAsInt(), group.get("name").getAsString());
          else if (child.isJsonObject()) {
            var nested = new com.google.gson.JsonArray();
            nested.add(child);
            groups(nested, names);
          }
      }
  }

  private static Matrix4d around(Vector3d pivot, Quaterniond rotation) {
    return new Matrix4d()
        .translation(pivot)
        .rotate(rotation)
        .translate(-pivot.x, -pivot.y, -pivot.z);
  }

  static Map<Part, Matrix4d> transforms(
      CouplerKind kind,
      double p,
      double peerProgress,
      double shift,
      boolean valid,
      boolean active,
      Vector3d toward,
      Vector3d peerUp) {
    var out = new EnumMap<Part, Matrix4d>(Part.class);
    var motion = CouplerMechanism.motion(kind, p);
    var base = new Matrix4d();
    if ((kind == CouplerKind.KNUCKLE || kind == CouplerKind.KNUCKLE_SPLIT)
        && valid
        && p > 0
        && toward != null) {
      var bearing = new Vector3d(.5, .5, .875 + shift);
      var mating =
          CouplerMating.head(
              bearing,
              toward,
              new Vector3d(0, 1, 0),
              peerUp,
              CouplerMechanism.nominalLength(kind, 0));
      double blend = Math.min(p, peerProgress);
      base.translate(new Vector3d(mating.bearing()).sub(bearing).mul(blend))
          .mul(around(bearing, new Quaterniond().slerp(mating.rotation(), blend)));
    }
    out.put(Part.MOUNT, new Matrix4d());
    out.put(Part.BODY, base);
    out.put(
        Part.JAW,
        new Matrix4d(base)
            .mul(
                around(
                    new Vector3d(11 / 16.0, .5, 5 / 16.0 + shift),
                    new Quaterniond().rotateY(Math.toRadians(48) * motion.jawOpen()))));
    out.put(Part.PIN, new Matrix4d(base).translate(0, .18 * Math.clamp(motion.pinLift(), 0, 1), 0));
    var link = new Matrix4d(base);
    var pivot = new Vector3d(.5, .5, .5 + shift);
    double insert = motion.linkInsertion();
    if (active && toward != null && toward.distanceSquared(pivot) < 16) {
      var centre = new Vector3d(.5, 7.653284439003997 / 16, 6.270598714406038 / 16 + shift);
      var destination = new Vector3d(pivot).add(toward).mul(.5);
      var delta = new Vector3d(toward).sub(pivot);
      var rotation = new Quaterniond();
      if (delta.lengthSquared() > .001)
        rotation
            .rotationTo(new Vector3d(0, 0, -1), delta.normalize())
            .rotateX(Math.toRadians(22.5));
      var animated = new Vector3d(centre).lerp(destination, insert);
      link.translate(animated)
          .rotate(new Quaterniond().slerp(rotation, insert))
          .translate(-centre.x, -centre.y, -centre.z);
    } else if (valid) link.mul(around(pivot, new Quaterniond().rotateX(-Math.PI / 2 * insert)));
    out.put(Part.LINK, link);
    var rigging = new Matrix4d(base);
    pivot.set(.5, .5, .75 + shift);
    double hang = Math.clamp(p / .35, 0, 1);
    if (active && toward != null && toward.distanceSquared(pivot) < 16) {
      var delta = new Vector3d(toward).sub(pivot);
      if (delta.lengthSquared() > .001)
        rigging.mul(
            around(
                pivot,
                new Quaterniond()
                    .slerp(
                        new Quaterniond().rotationTo(new Vector3d(0, -1, 0), delta.normalize()),
                        hang)));
    }
    out.put(Part.RIGGING, rigging);
    var screw = new Matrix4d(rigging);
    if (active)
      screw.mul(
          around(
              new Vector3d(.5, .22, .75 + shift),
              new Quaterniond().rotateY(Math.PI * 2 * motion.screwTurns())));
    out.put(Part.SCREW, screw);
    return out;
  }
}
