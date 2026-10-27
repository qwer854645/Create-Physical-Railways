package com.guidedrailways;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Plot-space to world-space conversion for physical bodies.
 *
 * <p>Sable keeps a body's blocks in a plot at very large coordinates and expresses the pose as a
 * rotation point in that same plot space plus an orientation and a world position. Rotating an
 * absolute plot coordinate instead of a plot-relative one is not a small error: it lands the result
 * tens of millions of blocks from the body. That mistake shipped once in the rerail preflight and
 * made every rerail refuse, so the conversion lives here with an offline check next to it.
 */
final class Frames {
  /** World point of a plot-space point under a body pose described by these four values. */
  static Vector3d toWorld(
      Vector3dc local, Quaterniondc orientation, Vector3dc rotationPoint, Vector3dc position) {
    return new Vector3d(local).sub(rotationPoint).rotate(orientation).add(position);
  }

  private Frames() {}
}
