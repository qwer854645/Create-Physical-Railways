package com.guidedrailways;

/**
 * What a schedule is doing, in the coarsest terms a caller needs.
 *
 * <p>The controller stores sixteen integer codes and eight booleans, and callers were reading the raw code to answer
 * questions like "is the train stopped" and "is it waiting for something". Those questions have one answer each, and
 * the mapping is easy to get wrong: {@code OVERSHOOT} is a stop, {@code SIGNAL_WAIT} is not an error, and
 * {@code FINISHED} is not paused.
 *
 * <p>This is a projection of the existing codes, not a replacement: storage and transitions stay where they are until
 * the controller itself is rewritten, so behaviour cannot change by introduction.
 */
enum ScheduleStage {
  /** No schedule at all. */
  IDLE,
  /** Finished its route. */
  COMPLETE,
  /** Composing a route or waiting for it. */
  PLANNING,
  /** Moving under its own commands. */
  RUNNING,
  /** Stopped on purpose, in a state the controller intends to leave. */
  HOLDING,
  /** Stopped on something the player has to fix or wait out. */
  BLOCKED,
  /** Waiting at a stop for a condition (passengers, cargo, a timer). */
  WAITING;

  static ScheduleStage of(int code) {
    return switch (code) {
      case ScheduleController.EMPTY -> IDLE;
      case ScheduleController.FINISHED -> COMPLETE;
      case ScheduleController.PLANNING -> PLANNING;
      case ScheduleController.TRAVELLING -> RUNNING;
      case ScheduleController.SIGNAL_WAIT -> WAITING;
      case ScheduleController.WAITING -> WAITING;
      case ScheduleController.PAUSED -> HOLDING;
      case ScheduleController.OVERSHOOT -> HOLDING;
      case ScheduleController.CHANGED -> HOLDING;
      case ScheduleController.UNSUPPORTED -> BLOCKED;
      case ScheduleController.NO_ROUTE -> BLOCKED;
      case ScheduleController.NO_POWER -> BLOCKED;
      case ScheduleController.TRACK_MISSING -> BLOCKED;
      case ScheduleController.CONFLICT -> BLOCKED;
      case ScheduleController.UNLOADED -> BLOCKED;
      case ScheduleController.EMERGENCY -> BLOCKED;
      // An unknown code is not "fine": it is the controller in a state this projection does not describe.
      default -> BLOCKED;
    };
  }

  /** True when the train may move under this schedule. */
  boolean moves() {
    return this == RUNNING;
  }

  /** True when the train is stopped for a reason that resolves without the player. */
  boolean benign() {
    return this == IDLE || this == COMPLETE || this == WAITING || this == PLANNING;
  }

  /** True when the player has something to fix. */
  boolean blocked() {
    return this == BLOCKED;
  }
}
