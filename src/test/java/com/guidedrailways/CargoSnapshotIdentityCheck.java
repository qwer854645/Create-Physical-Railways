package com.guidedrailways;

import java.util.*;

public final class CargoSnapshotIdentityCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var first = new Object();
    var replacement = new Object();
    var world = new HashMap<Integer, Object>();
    world.put(1, first);
    var owners = new StorageIdentities<Integer, Object>();
    owners.capture(1, first);
    check(owners.matches(world::get), "original container permits snapshot reuse");
    world.put(1, replacement);
    check(!owners.matches(world::get), "same-position replacement invalidates snapshot");
    world.remove(1);
    check(!owners.matches(world::get), "removed or unloaded container invalidates snapshot");
    world.put(1, first);
    check(owners.matches(world::get), "unchanged identity still matches");
    var cache = new ConsistCache<Integer, String>();
    cache.put(Set.of(1, 2), "train snapshot");
    cache.put(Set.of(3), "other train");
    var token = cache.token(1);
    check(cache.token(2) == token, "one capture shared by both carriage aliases");
    cache.invalidate(Set.of(2));
    check(
        cache.get(1) == null && cache.get(2) == null,
        "transfer on either carriage expires the complete shared capture");
    check(cache.get(3).equals("other train"), "other trains retain their cache");
    cache.put(Set.of(1, 2), "refreshed");
    check(cache.token(1) != token, "same-tick native views can detect refreshed capture");
    cache.put(Set.of(1), "split carriage");
    check(
        cache.get(2) == null && cache.get(1).equals("split carriage"),
        "split cannot leave an old sibling alias");
    cache.removeIf(value -> value.equals("split carriage"));
    check(cache.get(1) == null && cache.get(3) != null, "expiry removes only matching captures");
    var mutable = new HashSet<>(Set.of(4, 5));
    cache.put(mutable, "copied membership");
    mutable.clear();
    cache.invalidate(Set.of(4));
    check(cache.get(5) == null, "caller membership mutation cannot break alias invalidation");
    var wholeTrain = new StorageIdentities<Integer, Object>();
    var entities = new HashMap<Integer, Object>();
    for (int i = 0; i < 100; i++) {
      var entity = new Object();
      entities.put(i, entity);
      wholeTrain.capture(i, entity);
    }
    int[] lookups = {0};
    java.util.function.Function<Integer, Object> current =
        key -> {
          lookups[0]++;
          return entities.get(key);
        };
    var captures = new ConsistCache<Integer, StorageIdentities<Integer, Object>>();
    captures.put(Set.of(1, 2), wholeTrain);
    var beforeReplacement = captures.token(1);
    for (int carriage = 0; carriage < 20; carriage++)
      check(wholeTrain.matches(100, current), "carriages share this tick's identity validation");
    check(lookups[0] == 100, "20 views validate 100 entities once instead of 2000 times");
    check(
        wholeTrain.matches(101, current) && lookups[0] == 200,
        "next tick rechecks live identities");
    entities.put(0, replacement);
    // The entity lifecycle hook invalidates every carriage alias, even within this same tick.
    captures.invalidate(Set.of(2));
    check(captures.get(1) == null, "same-tick replacement expires the validated shared capture");
    var refreshed = new StorageIdentities<Integer, Object>();
    entities.forEach(refreshed::capture);
    captures.put(Set.of(1, 2), refreshed);
    check(
        captures.token(1) != beforeReplacement && captures.get(1).matches(101, current),
        "same-tick replacement creates a fresh capture and native view token");
    check(lookups[0] == 300, "replacement forces exactly one new complete identity validation");
    check(
        refreshed.matches(0, current) && lookups[0] == 400,
        "clock rollback cannot reuse future validation");
    entities.remove(0);
    check(!refreshed.matches(1, current), "missing container is rejected on the next validation");
    System.out.println(
        "CargoSnapshotIdentityCheck: replacement, removal, shared invalidation, same-tick tokens, split and expiry passed");
  }
}
