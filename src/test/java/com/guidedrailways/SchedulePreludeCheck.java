package com.guidedrailways;

import java.util.*;

public final class SchedulePreludeCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var applied = new ArrayList<Integer>();
    var initial = SchedulePrelude.run(5, false, 0, 32, i -> i == 2 || i == 4, applied::add);
    check(
        initial.entry() == 2 && applied.equals(List.of(0, 1)),
        "apply speed/title in order before travelling");
    applied.clear();
    var restored = SchedulePrelude.run(5, false, 2, 32, i -> i == 2 || i == 4, applied::add);
    check(
        restored.entry() == 2 && applied.isEmpty(),
        "restored destination must not replay past settings or reset wait progress");
    var next = SchedulePrelude.run(5, false, 3, 32, i -> i == 2 || i == 4, applied::add);
    check(next.entry() == 4 && applied.equals(List.of(3)), "settings between stops");
    applied.clear();
    var tail = SchedulePrelude.run(3, false, 2, 32, i -> false, applied::add);
    check(
        tail.finished() && tail.entry() == 3 && applied.equals(List.of(2)),
        "last setting finishes noncyclic program");
    applied.clear();
    var wrapped = SchedulePrelude.run(3, true, 2, 32, i -> i == 1, applied::add);
    check(
        wrapped.entry() == 1 && applied.equals(List.of(2, 0)),
        "cyclic metadata wraps in correct order");
    applied.clear();
    var endless = SchedulePrelude.run(2, true, 0, 32, i -> false, applied::add);
    check(
        !endless.finished() && endless.executed() == 32 && applied.size() == 32,
        "pure metadata loop cannot hang server tick");
    var longPrefix = SchedulePrelude.run(100, false, 0, 32, i -> i == 99, i -> {});
    check(
        longPrefix.entry() == 32 && !longPrefix.finished(),
        "long prefix yields without skipping entries");
    var continuation =
        SchedulePrelude.run(100, false, longPrefix.entry(), 32, i -> i == 99, i -> {});
    check(continuation.entry() == 64, "next tick resumes from yielded entry");
    check(
        SchedulePrelude.run(0, false, 0, 32, i -> false, i -> {}).finished(),
        "empty program finishes safely");
    double half = SchedulePrelude.ceiling(12.8, .5);
    check(Math.abs(half - 6.4) < 1e-9, "native 50 percent sets automatic speed ceiling");
    check(SchedulePrelude.ceiling(0, .5) == 0, "settings cannot manufacture power");
    var braking = AutomaticDriveModel.demand(100, 10, half, 4);
    check(
        braking.ceiling() <= half && braking.notch() < 0,
        "lowering limit requests braking when actual speed is too high");
    check(
        SchedulePrelude.throttle(Double.NaN) == 1
            && SchedulePrelude.throttle(-10) == .05
            && SchedulePrelude.throttle(100) == 1,
        "invalid values stay in native editor bounds");
    System.out.println("Schedule prelude and speed-limit checks passed");
  }
}
