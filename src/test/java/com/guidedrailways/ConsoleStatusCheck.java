package com.guidedrailways;

/**
 * Priority of the console's status reasons.
 *
 * <p>The ordering is the whole point of the type: a reason that outranks another tells the player what to fix
 * first, so the order is asserted rather than described.
 */
public final class ConsoleStatusCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected console status: " + what);
  }

  private static ConsoleStatus.Inputs all(boolean... ignored) {
    return new ConsoleStatus.Inputs(false, false, false, false, false, false, 0, false, false, false, false);
  }

  private static ConsoleStatus.Inputs bound() {
    return new ConsoleStatus.Inputs(true, false, false, false, false, false, 0, false, false, false, false);
  }

  public static void main(String[] args) {
    check(
        ConsoleStatus.of(all()) == ConsoleStatus.NO_BODY, "an unbound console drives nothing, whatever else");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, true, true, true, true, true, 3, true, true, true, true))
            == ConsoleStatus.STATION_GONE,
        "a missing station outranks everything it would prevent");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, true, true, true, true, 3, true, true, true, true))
            == ConsoleStatus.NO_ROUTE,
        "no route outranks the obstacles on the way");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, true, true, true, 3, true, true, true, true))
            == ConsoleStatus.SWITCH_DISABLED,
        "a disabled switch outranks one that is merely held");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, true, true, 3, true, true, true, true))
            == ConsoleStatus.SWITCH_HELD,
        "a held junction outranks a signal");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, true, 3, true, true, true, true))
            == ConsoleStatus.SIGNAL_BLOCKED,
        "a signal outranks schedule waits");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, false, 2, true, true, true, true))
            == ConsoleStatus.WAIT_PASSENGERS,
        "waiting passengers outranks waiting cargo");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, false, 0, true, true, true, true))
            == ConsoleStatus.WAIT_CARGO,
        "waiting cargo outranks a timer");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, false, 0, false, true, true, true))
            == ConsoleStatus.WAIT_TIMER,
        "a timer outranks standing at the stop");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, false, 0, false, false, true, true))
            == ConsoleStatus.ARRIVED,
        "arriving outranks the brake the train arrived with");
    check(
        ConsoleStatus.of(new ConsoleStatus.Inputs(true, false, false, false, false, false, 0, false, false, true, false))
            == ConsoleStatus.HOLD_BRAKE,
        "the brake is the last reason before ready");
    check(ConsoleStatus.of(bound()) == ConsoleStatus.READY, "a bound console with nothing in the way is ready");

    check(ConsoleStatus.STATION_GONE.actionable(), "a broken station is the player's to fix");
    check(ConsoleStatus.SWITCH_HELD.actionable(), "a held junction is the player's to wait out");
    check(!ConsoleStatus.WAIT_PASSENGERS.actionable(), "waiting for passengers is not a fault");
    check(!ConsoleStatus.READY.actionable(), "ready is not a fault");

    System.out.println("ConsoleStatusCheck: " + checks + " status priorities passed");
  }
}
