package com.guidedrailways;

import net.minecraft.world.phys.*;

/** Production contact latch and discovery cadence, including root/bogey sensor offsets. */
public final class PortalContactCheck {
  private static int checks;
  private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
  public static void main(String[] args) {
    var contact = new PortalContact();
    check(contact.enter(), "first contact attempts immediately, without player warmup");
    int repeats = 0;
    for (int tick = 0; tick < 12000; tick++) if (contact.enter()) repeats++;
    check(repeats == 0, "ten minutes in the door cannot re-arm a failed attempt or repeat heavy work");
    contact.leave(); check(contact.enter(), "leaving and reentering permits one new attempt");
    contact.block(); check(!contact.enter(), "a failure remains latched");
    contact.leave(); check(contact.enter(), "failure can be retried after exit");
    var arrival = new PortalContact(); arrival.block();
    repeats = 0; for (int tick = 0; tick < 400; tick++) if (arrival.enter()) repeats++;
    check(repeats == 0, "arrival inside a portal cannot bounce when its time cooldown ends");
    arrival.leave(); check(arrival.enter(), "arrival is eligible after leaving");
    var wholeTrain = new PortalContact(); int attempts = 0;
    for (int member = 0; member < 128; member++) if (wholeTrain.enter()) attempts++;
    check(attempts == 1, "carriages and bogeys sharing one contact cannot initiate separate transfers");
    wholeTrain.leave(); attempts = 0;
    for (int member = 0; member < 128; member++) if (wholeTrain.enter()) attempts++;
    check(attempts == 1, "a complete consist gets exactly one attempt on its next entry");
    var owner = new AABB(0, 1, 0, 1, 2, 1);
    var sensor = new AABB(0, 0, 0, 1, 2, 1);
    check(!PortalContact.scanDue(1, 20, owner.getCenter(), owner.getCenter()), "lower bogey extending the sensor box does not make a stationary carrier appear to move");
    check(PortalContact.scanDue(1, 20, owner.getCenter(), sensor.getCenter()), "fixture exposes the old owner/sensor-center comparison error");
    int scans = 0; long next = 0;
    for (long tick = 0; tick <= 1000; tick++) if (PortalContact.scanDue(tick, next, owner.getCenter(), owner.getCenter())) {
      scans++; next = tick + PortalContact.scanDelay(sensor, sensor);
    }
    check(scans == 51, "stationary negative results poll once per second, not every tick");
    check(PortalContact.scanDue(1, 20, owner.getCenter().add(.8, 0, 0), owner.getCenter()), "fast motion immediately bypasses a negative cached result");
    check(PortalContact.scanDelay(sensor, sensor.move(.1, 0, 0)) == 5, "moving sensor gets a short discovery interval");
    check(PortalContact.scanDue(20, 20, owner.getCenter(), owner.getCenter()), "negative-cache deadline is inclusive");
    System.out.println("PortalContactCheck: " + checks + " one-shot contact, exit reset, cooldown and shared-member checks passed");
  }
}
