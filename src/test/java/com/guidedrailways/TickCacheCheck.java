package com.guidedrailways;

public final class TickCacheCheck {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var cache = new TickCache<String, Boolean>(19);
    check(cache.get("junction", 100) == null, "first diagnostic must be allowed");
    cache.put("junction", true, 100);
    check(cache.get("junction", 119) != null, "diagnostic throttled before 20 ticks");
    check(cache.get("junction", 120) == null, "diagnostic allowed at 20 ticks");
    var waiting = new TickCache<String, Boolean>(59);
    waiting.put("junction", true, 100);
    check(waiting.get("junction", 159) != null, "queue log throttled before 60 ticks");
    check(waiting.get("junction", 160) == null, "queue log allowed at 60 ticks");
    var abandoned = new TickCache<Integer, String>(40);
    for (int i = 0; i < 1000; i++) abandoned.put(i, "old train", 100);
    abandoned.put(1000, "active train", 130);
    abandoned.tick(141);
    check(abandoned.size() == 1, "unqueried abandoned train keys are swept");
    check("active train".equals(abandoned.get(1000, 141)), "live key retained");
    abandoned.tick(0);
    check(abandoned.size() == 0, "clock rewind invalidates old records");
    var firstWorld = new TickCache<String, String>(60);
    var secondWorld = new TickCache<String, String>(60);
    firstWorld.put("same identity", "blocked", 10);
    check(secondWorld.get("same identity", 10) == null, "world caches are independent");
    System.out.println("TickCacheCheck: first log, throttle boundaries, abandoned keys and clock reset passed");
  }
}
