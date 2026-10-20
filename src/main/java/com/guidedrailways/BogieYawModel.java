package com.guidedrailways;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Weak native bearing spring; references live in the carrier's local coordinates. */
final class BogieYawModel {
  record Spring(double stiffness, double damping, double maxTorque) {}

  static Spring spring(int supports) {
    double share = 1.0 / Math.max(1, supports);
    return new Spring(12 * share, 18 * share, 120 * share);
  }

  static Quaterniond reference(Quaterniondc carrier, Quaterniondc bogie) {
    // Establish the bearing reference for a newly assembled mount; either direction is valid.
    var forward = new Vector3d(0, 0, 1).rotate(bogie).rotate(new Quaterniond(carrier).conjugate());
    return new Quaterniond().rotateY(forward.z < 0 ? Math.PI : 0);
  }

  static Quaterniond transfer(
      Quaterniondc previousCarrier, Quaterniondc localReference, Quaterniondc nextCarrier) {
    return new Quaterniond(nextCarrier)
        .conjugate()
        .mul(previousCarrier)
        .mul(localReference)
        .normalize();
  }
}
