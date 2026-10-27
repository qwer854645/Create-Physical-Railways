package com.guidedrailways;

/** The console display table and the guard that keeps the block state in step with it. */
public final class ConsoleDisplayCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(
        ConsoleDisplay.index(true, true, true, 8) == ConsoleDisplay.EMERGENCY,
        "emergency outranks a seated operator and a running schedule");
    check(
        ConsoleDisplay.index(true, false, false, 0) == ConsoleDisplay.EMERGENCY,
        "emergency is shown on an otherwise idle console");

    check(
        ConsoleDisplay.index(false, true, false, 1) == ConsoleDisplay.POWER,
        "a trace traction notch reads as power");
    check(
        ConsoleDisplay.index(false, true, false, -1) == ConsoleDisplay.BRAKE,
        "a trace brake notch reads as brake");
    check(
        ConsoleDisplay.index(false, true, false, -4) == ConsoleDisplay.BRAKE,
        "full service brake is brake, not neutral");
    check(
        ConsoleDisplay.index(false, true, false, 0) == ConsoleDisplay.IDLE,
        "a seated operator with no notch is idle");

    check(
        ConsoleDisplay.index(false, false, true, 0) == ConsoleDisplay.POWER,
        "an unstaffed console with a running schedule does not look switched off");
    check(
        ConsoleDisplay.index(false, false, true, 5) == ConsoleDisplay.POWER,
        "a leftover notch cannot change the unstaffed display");
    check(
        ConsoleDisplay.index(false, false, false, 0) == ConsoleDisplay.IDLE,
        "nothing running and nobody seated is idle");

    // Every index has to be inside the block state range, or the model variant cannot be selected.
    for (boolean emergency : new boolean[] {false, true})
      for (boolean active : new boolean[] {false, true})
        for (boolean observing : new boolean[] {false, true})
          for (int notch : new int[] {-4, -1, 0, 1, 8}) {
            int index = ConsoleDisplay.index(emergency, active, observing, notch);
            check(
                index >= 0 && index < ConsoleDisplay.COUNT,
                "index " + index + " must select one of the " + ConsoleDisplay.COUNT + " variants");
          }
    System.out.println("ConsoleDisplayCheck: " + checks + " display state checks passed");
  }
}
