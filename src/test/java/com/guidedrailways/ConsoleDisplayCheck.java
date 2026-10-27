package com.guidedrailways;

/** The lamp table: which lamps light, in what colour, and when they must stay dark. */
public final class ConsoleDisplayCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static final int ALL = ConsoleDisplay.LEFT | ConsoleDisplay.MIDDLE | ConsoleDisplay.RIGHT;

  public static void main(String[] args) {
    check(
        ConsoleDisplay.lampMask(false, false, 0) == 0,
        "a console with nothing bound stays dark");
    check(
        ConsoleDisplay.lampMask(false, true, -4) == 0,
        "an unbound console stays dark even at emergency");

    check(
        ConsoleDisplay.lampMask(true, false, 1) == ConsoleDisplay.LEFT,
        "traction lights only the left lamp");
    check(
        ConsoleDisplay.lampMask(true, false, 8) == ConsoleDisplay.LEFT,
        "the top notch is still traction");
    check(
        ConsoleDisplay.lampMask(true, false, 0) == ConsoleDisplay.MIDDLE,
        "no notch lights the middle lamp, not nothing");
    check(
        ConsoleDisplay.lampMask(true, false, -1) == ConsoleDisplay.RIGHT,
        "a trace brake notch lights the right lamp");
    check(
        ConsoleDisplay.lampMask(true, false, -4) == ConsoleDisplay.RIGHT,
        "full service brake is braking, not standby");
    check(
        ConsoleDisplay.lampMask(true, true, 5) == ALL,
        "emergency lights all three lamps");
    check(
        ConsoleDisplay.lampMask(true, true, -4) == ALL,
        "emergency lights all three even while braking");

    check(ConsoleDisplay.colour(ConsoleDisplay.LEFT, false) == ConsoleDisplay.GREEN, "traction is green");
    check(ConsoleDisplay.colour(ConsoleDisplay.MIDDLE, false) == ConsoleDisplay.AMBER, "standby is amber");
    check(ConsoleDisplay.colour(ConsoleDisplay.RIGHT, false) == ConsoleDisplay.RED, "braking is red");
    check(
        ConsoleDisplay.colour(ConsoleDisplay.LEFT, true) == ConsoleDisplay.RED
            && ConsoleDisplay.colour(ConsoleDisplay.MIDDLE, true) == ConsoleDisplay.RED
            && ConsoleDisplay.colour(ConsoleDisplay.RIGHT, true) == ConsoleDisplay.RED,
        "emergency paints every lamp red");

    // The seat is not part of the table: a bound console always shows exactly one driving lamp.
    for (int notch : new int[] {-4, -1, 0, 1, 8}) {
      int mask = ConsoleDisplay.lampMask(true, false, notch);
      check(
          mask == ConsoleDisplay.LEFT || mask == ConsoleDisplay.MIDDLE || mask == ConsoleDisplay.RIGHT,
          "exactly one lamp is lit without emergency");
    }
    System.out.println("ConsoleDisplayCheck: " + checks + " lamp table checks passed");
  }
}