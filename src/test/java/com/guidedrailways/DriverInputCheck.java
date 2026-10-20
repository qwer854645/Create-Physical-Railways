package com.guidedrailways;

public final class DriverInputCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    check(
        !DriverInputPolicy.suppressMovement(false, false), "ordinary play does not lose movement");
    check(
        DriverInputPolicy.suppressMovement(true, false),
        "active in-world driving suppresses horizontal movement");
    check(
        !DriverInputPolicy.suppressMovement(true, true),
        "GUI inputs are not intercepted by the driving movement handler");
    check(DriverInputPolicy.steer(false, false) == 0, "no steering input is default routing");
    check(DriverInputPolicy.steer(true, true) == 0, "opposed steering inputs are default routing");
    check(
        DriverInputPolicy.steer(true, false) == -1 && DriverInputPolicy.steer(false, true) == 1,
        "only left/right input maps to route intent");
    double gain = DriverInputPolicy.heldMouseSensitivity() * .6F + .2F;
    check(
        gain == 0,
        "held drag zeroes this frame's mouse turn gain exactly, without a persistent option edit");
    for (int id :
        new int[] {-1, 0, 3, 4, 5, 6, 7, 10, 14, 18, 23, 24, 35, 95, 109, Integer.MAX_VALUE})
      check(
          !DriverInputPolicy.remoteCommand(id),
          "world packets cannot claim arbitrary consoles or invoke GUI actions");
    for (int id : new int[] {0, 8, 19, 35})
      check(
          DriverInputPolicy.permittedWhileObserving(id),
          "observer can leave, emergency brake or explicitly take control");
    for (int id : new int[] {1, 2, 3, 4, 5, 6, 7, 9, 96, 100, 108, 20, 21, 22})
      check(
          !DriverInputPolicy.permittedWhileObserving(id),
          "observer cannot change throttle, reverser or schedule route through driving input");
    for (int id : new int[] {1, 2, 8, 9, 96, 100, 108, 19, 20, 21, 22})
      check(DriverInputPolicy.remoteCommand(id), "allowed world command remains available");
    for (int notch = -4; notch <= 8; notch++)
      check(
          DriverInputPolicy.notchCommand(DriverInputPolicy.NOTCH_BASE + notch),
          "all brake and traction commands occupy a separate non-steering range");
    check(
        !DriverInputPolicy.suppressLook(true, false, false, false),
        "takeover alone never captures the mouse");
    check(
        !DriverInputPolicy.suppressLook(true, false, false, true),
        "initial right-click alone never captures looking");
    check(
        !DriverInputPolicy.suppressLook(true, true, false, false),
        "releasing right immediately restores looking");
    check(
        !DriverInputPolicy.suppressLook(false, true, false, true),
        "stale drag after takeover ends cannot capture looking");
    check(
        !DriverInputPolicy.suppressLook(true, true, true, true),
        "GUI mouse is never captured by driving");
    check(
        DriverInputPolicy.suppressLook(true, true, false, true),
        "only an explicit held-right throttle drag consumes looking");
    System.out.println("DriverInputCheck: " + checks + " assertions passed");
  }
}
