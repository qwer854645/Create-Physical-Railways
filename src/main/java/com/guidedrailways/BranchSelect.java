package com.guidedrailways;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Directional branch intent and travel helpers shared by routing and physics. */
public final class BranchSelect {
  public enum Steer {
    NONE(0),
    LEFT(-1),
    RIGHT(1);
    final float targetDot;

    Steer(float t) {
      targetDot = t;
    }
  }

  private BranchSelect() {}

  /** Actual motion wins over throttle; near rest retain direction to avoid jitter. */
  public static boolean reverseTravel(double speed, double rpm, boolean previous) {
    if (Double.isFinite(speed) && Math.abs(speed) > .02) return speed < 0;
    if (Double.isFinite(speed)
        && Math.abs(speed) < .005
        && Double.isFinite(rpm)
        && Math.abs(rpm) > .001) return rpm < 0;
    return previous;
  }

  public static boolean exitsIncreasing(
      Vector3dc increasing, Vector3dc bodyForward, boolean reverse) {
    return increasing.dot(bodyForward) * (reverse ? -1 : 1) >= 0;
  }

  /** Default is the smallest geometric turn; explicit intent selects the left/right-most exit. */
  public static int choose(Vector3dc incoming, Vector3dc up, Vector3dc[] exits, Steer intent) {
    if (exits == null || exits.length == 0) return -1;
    var forward = new Vector3d(incoming).normalize();
    var right = new Vector3d(forward).cross(up).normalize();
    int best = 0;
    double score = -Double.MAX_VALUE, alignment = -Double.MAX_VALUE;
    for (int i = 0; i < exits.length; i++) {
      var exit = new Vector3d(exits[i]).normalize();
      double dot = forward.dot(exit);
      double rank = intent == Steer.NONE ? dot : intent.targetDot * right.dot(exit);
      if (rank > score + 1e-9 || Math.abs(rank - score) <= 1e-9 && dot > alignment + 1e-9) {
        best = i;
        score = rank;
        alignment = dot;
      }
    }
    return best;
  }
}
