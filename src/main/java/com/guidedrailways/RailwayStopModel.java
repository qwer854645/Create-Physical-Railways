package com.guidedrailways;

final class RailwayStopModel {
  static double force(
      double speed, double traction, double distance, double mass, double braking, double dt) {
    if (!Double.isFinite(distance)) return traction;
    double room = Math.max(0, distance), limit = Math.sqrt(2 * braking * room);
    if (room <= Math.abs(speed) * dt + .08) return -speed * mass / dt;
    if (Math.abs(speed) > limit)
      return -Math.signum(speed) * mass * Math.min(braking, Math.abs(speed) / dt);
    return room < .2 ? 0 : traction;
  }
}
