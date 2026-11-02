package com.guidedrailways;

public final class DriverInputCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    double gain = DriverInputPolicy.heldMouseSensitivity() * .6F + .2F;
    check(
        gain == 0,
        "held drag zeroes this frame's mouse turn gain exactly, without a persistent option edit");
    for (int id :
        new int[] {-1, 0, 1, 2, 3, 4, 5, 6, 7, 10, 14, 18, 20, 21, 22, 24, 95, 109, Integer.MAX_VALUE})
      check(
          !DriverInputPolicy.remoteCommand(id),
          "world packets reject legacy mode, keyboard steering and menu actions");
    for (int id : new int[] {8, 9, 96, 100, 108, 19, 23, 35})
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
