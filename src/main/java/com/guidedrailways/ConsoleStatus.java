package com.guidedrailways;

/**
 * Why a console's train is not moving, in one value.
 *
 * <p>Every one of these conditions already exists somewhere in the mod, and the player sees none of them: a
 * train that stands still looks the same whether a switch is disabled, another consist holds the junction, the
 * station was broken, the schedule is waiting for passengers, or the brake is simply on. This is the single
 * place where those conditions are folded into one reason with a fixed priority, so that the console can show
 * it and the log can name it.
 *
 * <p>Pure on purpose: the caller gathers the flags, this decides the answer, and the ordering is asserted.
 */
public enum ConsoleStatus {
  /** No plate is installed, or none of its bodies could be resolved: the console drives nothing. */
  NO_BODY,
  /** A station in the schedule no longer exists. */
  STATION_GONE,
  /** No route to the next target could be computed. */
  NO_ROUTE,
  /** The junction ahead is disabled and cannot be thrown. */
  SWITCH_DISABLED,
  /** Another consist holds the junction ahead. */
  SWITCH_HELD,
  /** A physical signal is not clear. */
  SIGNAL_BLOCKED,
  /** The schedule is waiting for passengers to board or leave. */
  WAIT_PASSENGERS,
  /** The schedule is waiting for cargo. */
  WAIT_CARGO,
  /** The schedule is holding the train at a station for a timed wait. */
  WAIT_TIMER,
  /** The brake is on and nothing else is stopping the train. */
  HOLD_BRAKE,
  /** Standing at the target stop. */
  ARRIVED,
  /** Nothing in the way: the train is free to move. */
  READY;

  /** Inputs, gathered by the caller from state that already exists. */
  public record Inputs(
      boolean bound,
      boolean stationMissing,
      boolean routeMissing,
      boolean switchDisabled,
      boolean switchHeld,
      boolean signalBlocked,
      int waitingPassengers,
      boolean waitingCargo,
      boolean waitingTimer,
      boolean brakeOn,
      boolean atStop) {}

  /**
   * The reason to show, chosen by priority.
   *
   * <p>Order matters: a broken station outranks a disabled switch, because the switch cannot be fixed while the
   * destination does not exist, and a held junction outranks a signal, because releasing it is what the player
   * can act on.
   */
  public static ConsoleStatus of(Inputs in) {
    if (!in.bound()) return NO_BODY;
    if (in.stationMissing()) return STATION_GONE;
    if (in.routeMissing()) return NO_ROUTE;
    if (in.switchDisabled()) return SWITCH_DISABLED;
    if (in.switchHeld()) return SWITCH_HELD;
    if (in.signalBlocked()) return SIGNAL_BLOCKED;
    if (in.waitingPassengers() > 0) return WAIT_PASSENGERS;
    if (in.waitingCargo()) return WAIT_CARGO;
    if (in.waitingTimer()) return WAIT_TIMER;
    if (in.atStop()) return ARRIVED;
    if (in.brakeOn()) return HOLD_BRAKE;
    return READY;
  }

  /** True when the train cannot move for a reason the player can act on. */
  public boolean actionable() {
    return this == STATION_GONE
        || this == SWITCH_DISABLED
        || this == SWITCH_HELD
        || this == SIGNAL_BLOCKED
        || this == HOLD_BRAKE;
  }
}
