package com.guidedrailways;

/** A console has exactly one intent, and the pairs that must never coexist are asserted here. */
public final class ConsoleIntentCheck {
  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("unexpected console intent: " + what);
  }

  public static void main(String[] args) {
    check(ConsoleIntent.of(false, true, true, true, true) == ConsoleIntent.IDLE, "an unbound console is idle");
    check(ConsoleIntent.of(true, false, false, false, false) == ConsoleIntent.IDLE, "an unused console is idle");
    check(ConsoleIntent.of(true, false, false, false, true) == ConsoleIntent.MANUAL, "a seated driver is manual");
    check(ConsoleIntent.of(true, true, false, false, true) == ConsoleIntent.ALIGN, "aligning outranks manual");
    check(
        ConsoleIntent.of(true, true, true, true, true) == ConsoleIntent.ALIGN,
        "aligning outranks a running schedule it was asked for from");
    check(
        ConsoleIntent.of(true, false, true, true, true) == ConsoleIntent.SCHEDULE,
        "a running schedule being observed outranks the lever");
    check(
        ConsoleIntent.of(true, false, true, false, true) == ConsoleIntent.OVERRIDE,
        "a driver taking a schedule's train is an override");
    check(
        ConsoleIntent.of(true, false, true, false, false) == ConsoleIntent.OVERRIDE,
        "an override does not need the lever to be active to be recognised");

    // Exactly one intent, whatever the flags say: this is the property the old boolean set did not have.
    for (int bound = 0; bound < 2; bound++)
      for (int aligning = 0; aligning < 2; aligning++)
        for (int running = 0; running < 2; running++)
          for (int observing = 0; observing < 2; observing++)
            for (int active = 0; active < 2; active++) {
              var intent =
                  ConsoleIntent.of(
                      bound == 1, aligning == 1, running == 1, observing == 1, active == 1);
              checks++;
              if (intent == null) throw new AssertionError("intent was null for a flag combination");
            }

    check(ConsoleIntent.IDLE.drives() == false, "an idle console drives nothing");
    check(ConsoleIntent.SCHEDULE.drives() == false, "a schedule drives through its own commands");
    check(ConsoleIntent.MANUAL.drives() && ConsoleIntent.ALIGN.drives(), "manual and align drive");
    check(ConsoleIntent.OVERRIDE.drives(), "an override drives");
    check(ConsoleIntent.SCHEDULE.scheduled(), "the schedule intent is the scheduled one");
    check(!ConsoleIntent.OVERRIDE.scheduled(), "an override is not scheduled driving");

    System.out.println("ConsoleIntentCheck: " + checks + " console intents passed");
  }
}
