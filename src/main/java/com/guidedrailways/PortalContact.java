package com.guidedrailways;

import net.minecraft.world.phys.*;

/** One immediate attempt per continuous contact, shared by a complete physical consist. */
final class PortalContact {
  private boolean spent;

  boolean enter() {
    if (spent) return false;
    spent = true; return true;
  }

  void block() { spent = true; }
  void leave() { spent = false; }

  static boolean scanDue(long now, long next, Vec3 center, Vec3 lastOwnerCenter) {
    return now >= next || center.distanceToSqr(lastOwnerCenter) >= .0625;
  }

  static int scanDelay(AABB before, AABB after) {
    boolean still = after.getCenter().distanceToSqr(before.getCenter()) < 1e-6
        && Math.abs(after.getXsize() - before.getXsize()) + Math.abs(after.getYsize() - before.getYsize())
            + Math.abs(after.getZsize() - before.getZsize()) < 1e-5;
    return still ? 20 : 5;
  }
}
