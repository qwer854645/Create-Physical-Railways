package com.guidedrailways;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which rotations a bearing keeps rigid.
 *
 * <p>Body-local X runs along travel, so rotation about X is roll and rotation about Z is pitch.
 * Both must stay rigid, or a heavy plate banks and nods on its own under the drive impulse that is
 * applied below it. Yaw is never locked: a bearing has to articulate under the body, otherwise the
 * bogies of one carrier give that body conflicting headings in every curve. It is only centred, and
 * the centring budget is shared between the bogies rather than multiplied on one body.
 */
final class BearingLock {
  enum Axis {
    ROLL,
    PITCH,
    YAW
  }

  /** Rigid axes for a carrier with this many bearings; a single bearing is no different. */
  static Set<Axis> locked(int supports) {
    return EnumSet.of(Axis.ROLL, Axis.PITCH);
  }

  /** Share of the centring budget one bearing receives when a carrier has several. */
  static double share(int supports) {
    return 1.0 / Math.max(1, supports);
  }

  private BearingLock() {}
}
