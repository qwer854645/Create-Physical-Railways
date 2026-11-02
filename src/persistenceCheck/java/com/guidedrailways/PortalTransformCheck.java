package com.guidedrailways;

import net.minecraft.world.phys.*;
import net.minecraft.nbt.*;

public final class PortalTransformCheck {
  private static int checks;
  private static void check(boolean result, String message) {
    checks++; if (!result) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    var origin = new Vec3(2, 65, -3);
    var destination = new Vec3(-20, 70, 40);
    var box = new AABB(-1, 64, -8, 5, 68, 4);
    for (int turn = -4; turn <= 4; turn++) {
      var mapping = new PortalTransform(origin, destination, turn);
      var inverse = new PortalTransform(destination, origin, -turn);
      var result = mapping.bounds(box);
      for (double x : new double[] {box.minX, box.maxX})
        for (double y : new double[] {box.minY, box.maxY})
          for (double z : new double[] {box.minZ, box.maxZ}) {
            var point = new Vec3(x, y, z);
            var moved = mapping.point(point);
            check(inverse.point(moved).distanceTo(point) < 1e-9, "quarter-turn round trip preserves every corner");
            check(moved.x >= result.minX && moved.x <= result.maxX
                && moved.y >= result.minY && moved.y <= result.maxY
                && moved.z >= result.minZ && moved.z <= result.maxZ, "rotated bounds contain every corner");
          }
      check(Math.abs(result.getXsize() * result.getYsize() * result.getZsize() - 288) < 1e-9,
          "portal rotation does not scale the body");
    }
    for (boolean sourceX : new boolean[] {true, false}) for (boolean targetX : new boolean[] {true, false})
      for (int direction : new int[] {-1, 1}) {
        var entry = new PortalTransform.Opening(new Vec3(0, 64, 0), sourceX, 8, 8);
        var exit = new PortalTransform.Opening(new Vec3(100, 70, 100), targetX, 8, 8);
        var group = sourceX ? new AABB(-1, 64, -8, 1, 67, 8) : new AABB(-8, 64, -1, 8, 67, 1);
        var speed = sourceX ? new Vec3(0, 0, direction) : new Vec3(direction, 0, 0);
        var mapping = PortalTransform.passage(entry, exit, group, new Vec3(0, 65, 0), speed);
        var moved = mapping.bounds(group);
        check(exit.fits(moved), "rotated group fits the exit width and height");
        double velocity = exit.normal(mapping.vector(speed));
        double near = targetX ? velocity > 0 ? moved.minZ : moved.maxZ : velocity > 0 ? moved.minX : moved.maxX;
        check(Math.abs(near - (100 + Math.signum(velocity) * .75)) < 1e-9,
            "entire carriage is placed outside the portal, in either travel direction");
        var tooSmall = new PortalTransform.Opening(new Vec3(100, 70, 100), targetX, 1, 2);
        var oversized = PortalTransform.passage(entry, tooSmall, group, new Vec3(0, 65, 0), speed).bounds(group);
        check(Math.abs((targetX ? oversized.getCenter().x : oversized.getCenter().z) - 100) < 1e-9,
            "narrow exit centers the complete body instead of rejecting contact");
        check(Math.abs(oversized.minY - 70.05) < 1e-9, "tall body clears the exit floor without floating out of support range");
        double outside = targetX ? velocity > 0 ? oversized.minZ : oversized.maxZ : velocity > 0 ? oversized.minX : oversized.maxX;
        check(Math.abs(outside - (100 + Math.signum(velocity) * .75)) < 1e-9,
            "an oversized body still clears the entire portal frame along its normal");
      }
    for (boolean widthX : new boolean[] {true, false}) {
      var entry = new PortalTransform.Opening(new Vec3(0, 64, 0), widthX, 2, 3);
      var target = new PortalTransform.Opening(new Vec3(100, 70, 100), widthX, 2, 3);
      // Lower bogeys below the entrance, a wide body, a long tail, and an offset entry used to
      // all be rejected by whole-box containment even when the destination had clear space.
      for (var source : new AABB[] {new AABB(-2, 63.7, -20, 2, 68, 20),
          new AABB(.9, 64, -.2, 2.4, 66, .2), new AABB(-.5, 65.9, -1, .5, 68.2, 1)}) {
        var group = widthX ? source : new AABB(source.minZ, source.minY, source.minX, source.maxZ, source.maxY, source.maxX);
        check(!entry.fits(group), "regression fixture really fails the previous containment precondition");
        var anchor = group.getCenter(); var result = PortalTransform.passage(entry, target, group, anchor, Vec3.ZERO).bounds(group);
        double center = widthX ? result.getCenter().x : result.getCenter().z;
        check(Double.isFinite(center) && result.minY >= 70, "partial and oversized contact has a finite destination above its floor");
        check((widthX ? result.minZ : result.minX) >= 100.75 - 1e-9
            || (widthX ? result.maxZ : result.maxX) <= 99.25 + 1e-9, "the complete group clears the frame, including its tail");
        check(Math.abs(group.getXsize() - result.getXsize()) < 1e-8 && Math.abs(group.getYsize() - result.getYsize()) < 1e-8,
            "exit planning never shrinks a large structure to fake a fit");
      }
    }
    var schedule = new CompoundTag();
    var item = new CompoundTag(); item.putString("id", "create:schedule"); item.putInt("count", 1);
    schedule.put("Item", item.copy()); schedule.putInt("Entry", 3); schedule.putString("Title", "Express");
    schedule.putBoolean("Running", true); schedule.putBoolean("Waiting", true);
    schedule.putUUID("Station", java.util.UUID.randomUUID()); schedule.put("Conditions", new ListTag());
    var driver = new CompoundTag(); driver.putString("id", "guidedrailways:driver_console");
    driver.put("PhysicalSchedule", schedule); driver.putInt("Notch", 8);
    var cargo = new CompoundTag(); cargo.putString("id", "minecraft:chest"); cargo.putString("CustomName", "Cargo");
    var entities = new ListTag(); entities.add(driver); entities.add(cargo);
    var chunk = new CompoundTag(); chunk.put("block_entities", entities);
    var chunks = new CompoundTag(); chunks.put("0", chunk);
    var plot = new CompoundTag(); plot.put("chunks", chunks);
    var body = new CompoundTag(); body.put("plot", plot);
    TransferControls.park(body);
    check(schedule.getCompound("Item").equals(item) && schedule.getInt("Entry") == 3
        && schedule.getString("Title").equals("Express"), "portal parking preserves the real schedule item and itinerary progress");
    check(!schedule.getBoolean("Running") && !schedule.getBoolean("Waiting")
        && !schedule.contains("Station") && !schedule.contains("Conditions"), "old world station waits cannot resume at the exit");
    check(driver.getBoolean("Emergency") && driver.getInt("Notch") == -4, "arrival requires explicit brake release");
    check(cargo.getString("CustomName").equals("Cargo") && !cargo.contains("Emergency"), "parking cannot alter cargo");
    var once = body.copy(); TransferControls.park(body);
    check(body.equals(once), "repeated arrival parking is idempotent");
    check(PortalTransform.cooling(100, 200, 100), "fresh cooldown blocks immediate bounce-back");
    check(!PortalTransform.cooling(200, 200, 100), "cooldown expires at its boundary");
    check(!PortalTransform.cooling(20, 200, 100), "clock rollback cannot leave an indefinitely blocked body");
    check(!PortalTransform.cooling(100, 0, 100), "old bodies without cooldown remain eligible");
    System.out.println("PortalTransformCheck: " + checks + " portal orientation, clearance, parking and round-trip checks passed");
  }
}
