package com.physicalrailways;

/** Narrow input ownership policy shared by networking and client regression checks. */
public final class DriverInputPolicy {
  private DriverInputPolicy() {}

  public static final int NOTCH_BASE = 100;

  public static boolean notchCommand(int id) {
    return id >= NOTCH_BASE - DriverModel.BRAKE_NOTCHES && id <= NOTCH_BASE + DriverModel.MAX_NOTCH;
  }

  public static boolean remoteCommand(int id) {
    return id == 35
        || id == 8
        || id == 9
        || id == 19
        || id == 23
        || id >= 20 && id <= 22
        || notchCommand(id);
  }

  public static boolean suppressLook(
      boolean active, boolean dragging, boolean gui, boolean rightDown) {
    return active && dragging && !gui && rightDown;
  }

  /**
   * Current pinned Minecraft turn equation: sensitivity*.6F+.2F. Per-event gain, never a saved
   * option.
   */
  public static double heldMouseSensitivity() {
    return -(double) .2F / (double) .6F;
  }

}
