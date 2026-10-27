package com.guidedrailways;

/** The console lamp table: what each lamp means, and that nothing else can select. */
public final class ConsoleDisplayCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(
        ConsoleDisplay.index(false, false, 0) == ConsoleDisplay.OFF,
        "a console with nothing bound goes dark");
    check(
        ConsoleDisplay.index(false, true, -4) == ConsoleDisplay.OFF,
        "an unbound console stays dark even with the lever at emergency");

    check(
        ConsoleDisplay.index(true, false, 1) == ConsoleDisplay.POWER,
        "traction lights the left lamp");
    check(
        ConsoleDisplay.index(true, false, 8) == ConsoleDisplay.POWER,
        "the top notch is still traction");
    check(
        ConsoleDisplay.index(true, false, 0) == ConsoleDisplay.STANDBY,
        "no notch is standby, not dark");
    check(
        ConsoleDisplay.index(true, false, -1) == ConsoleDisplay.BRAKE,
        "a trace brake notch lights the right lamp");
    check(
        ConsoleDisplay.index(true, false, -4) == ConsoleDisplay.BRAKE,
        "full service brake is braking, not standby");
    check(
        ConsoleDisplay.index(true, true, 5) == ConsoleDisplay.EMERGENCY,
        "emergency outranks a traction notch");
    check(
        ConsoleDisplay.index(true, true, -4) == ConsoleDisplay.EMERGENCY,
        "emergency outranks braking");

    // The seat is not part of the table: leaving a console must not change what the lamps show.
    for (int notch : new int[] {-4, -1, 0, 1, 8}) {
      int index = ConsoleDisplay.index(true, false, notch);
      check(
          index == ConsoleDisplay.POWER
              || index == ConsoleDisplay.STANDBY
              || index == ConsoleDisplay.BRAKE,
          "a bound console always shows one of the driving lamps");
      check(index >= 0 && index < ConsoleDisplay.COUNT, "index selects a model variant");
    }
    System.out.println("ConsoleDisplayCheck: " + checks + " lamp table checks passed");
  }
}
