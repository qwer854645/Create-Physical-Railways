package com.guidedrailways;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ObserverFilterCacheCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var cache = new ObserverFilterCache();
    var filter = new String("iron");
    var calls = new AtomicInteger();
    for (int tick = 0; tick < 100; tick++)
      check(
          cache.test(
              filter,
              List.of(1, 2),
              true,
              () -> {
                calls.incrementAndGet();
                return true;
              }),
          "same revision reuses match");
    check(calls.get() == 1, "stationary coverage does not rescan inventory each tick");
    check(
        !cache.test(filter, List.of(2, 1), true, () -> false),
        "equal revision sums cannot conceal a carriage change");
    check(
        cache.test(new String("iron"), List.of(2, 1), true, () -> true),
        "replacing a filter invalidates its identity");
    check(
        !cache.test(
            filter,
            List.of(2, 1),
            false,
            () -> {
              throw new AssertionError("unloaded cargo must not be read");
            }),
        "incomplete inventory is not a match");
    check(
        cache.test(filter, List.of(2, 1), true, () -> true),
        "load recovery cannot reuse stale negative result");
    check(
        !cache.test(filter, List.of(2, 1, 0), true, () -> false),
        "new carriage invalidates decision");
    cache.clear();
    check(
        cache.test(filter, List.of(2, 1, 0), true, () -> true),
        "explicit removal clears old match");
    for (int i = 0; i < 1000; i++) cache.test(new Object(), List.of(9), true, () -> false);
    check(
        !cache.test(filter, List.of(9), true, () -> false),
        "bounded cache survives many changed filters");
    System.out.println("ObserverFilterCacheCheck: " + checks + " checks passed");
  }
}
