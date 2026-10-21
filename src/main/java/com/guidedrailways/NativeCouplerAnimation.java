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

  /** Caller-owned scratch; matrices are consumed immediately and never retained by shapes. */
  static final class Workspace {
    final EnumMap<Part, Matrix4d> parts = new EnumMap<>(Part.class);
    final Matrix4d around = new Matrix4d();
    final Vector3d pivot = new Vector3d(),
        centre = new Vector3d(),
        destination = new Vector3d(),
        delta = new Vector3d(),
        animated = new Vector3d();
    final Quaterniond rotation = new Quaterniond(), blended = new Quaterniond();

    Workspace() {
      for (var part : Part.values()) parts.put(part, new Matrix4d());
    }
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

  private static Matrix4d around(Vector3d pivot, Quaterniond rotation, Workspace workspace) {
    return workspace
        .around
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
      Vector3d peerUp,
      Workspace workspace) {
    var out = workspace.parts;
    var motion = CouplerMechanism.motion(p);
    var base = out.get(Part.BODY).identity();
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
          .mul(around(bearing, new Quaterniond().slerp(mating.rotation(), blend), workspace));
    }
    out.get(Part.MOUNT).identity();
    out.get(Part.JAW)
        .set(base)
        .mul(
            around(
                workspace.pivot.set(11 / 16.0, .5, 5 / 16.0 + shift),
                workspace.rotation.identity().rotateY(Math.toRadians(48) * motion.jawOpen()),
                workspace));
    out.get(Part.PIN).set(base).translate(0, .18 * Math.clamp(motion.pinLift(), 0, 1), 0);
    var link = out.get(Part.LINK).set(base);
    var pivot = workspace.pivot.set(.5, .5, .5 + shift);
    double insert = motion.linkInsertion();
    if (active && toward != null && toward.distanceSquared(pivot) < 16) {
      var centre = workspace.centre.set(.5, 7.653284439003997 / 16, 6.270598714406038 / 16 + shift);
      var destination = workspace.destination.set(pivot).add(toward).mul(.5);
      var delta = workspace.delta.set(toward).sub(pivot);
      var rotation = workspace.rotation.identity();
      if (delta.lengthSquared() > .001)
        rotation
            .rotationTo(new Vector3d(0, 0, -1), delta.normalize())
            .rotateX(Math.toRadians(22.5));
      var animated = workspace.animated.set(centre).lerp(destination, insert);
      link.translate(animated)
          .rotate(workspace.blended.identity().slerp(rotation, insert))
          .translate(-centre.x, -centre.y, -centre.z);
    } else if (valid)
      link.mul(
          around(pivot, workspace.rotation.identity().rotateX(-Math.PI / 2 * insert), workspace));
    var rigging = out.get(Part.RIGGING).set(base);
    pivot.set(.5, .5, .75 + shift);
    double hang = Math.clamp(p / .35, 0, 1);
    if (active && toward != null && toward.distanceSquared(pivot) < 16) {
      var delta = workspace.delta.set(toward).sub(pivot);
      if (delta.lengthSquared() > .001)
        rigging.mul(
            around(
                pivot,
                workspace
                    .blended
                    .identity()
                    .slerp(
                        workspace.rotation.rotationTo(
                            workspace.centre.set(0, -1, 0), delta.normalize()),
                        hang),
                workspace));
    }
    var screw = out.get(Part.SCREW).set(rigging);
    if (active)
      screw.mul(
          around(
              workspace.centre.set(.5, .22, .75 + shift),
              workspace.rotation.identity().rotateY(Math.PI * 2 * motion.screwTurns()),
              workspace));
    return out;
  }
}
