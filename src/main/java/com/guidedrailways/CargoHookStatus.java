package com.guidedrailways;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Safe to initialize during Mixin discovery: no Minecraft or optional-mod classes. */
public final class CargoHookStatus {
  private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

  public static void disable(String feature) {
    DISABLED.add(feature);
  }

  public static boolean ready(String feature) {
    return !DISABLED.contains(feature);
  }
}
