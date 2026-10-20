package com.guidedrailways;

/** One command for both manual driving and schedules; null command means direct RPM drive. */
record TrainCommand(DriverConsoleEntity owner, ScheduleController navigation, Mode mode,
                    int sign, double ceiling, int notch, boolean emergency, boolean hold) {
    enum Mode { MANUAL, SCHEDULE, HOLD }
}
