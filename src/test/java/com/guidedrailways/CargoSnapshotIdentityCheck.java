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
    System.out.println(
        "CargoSnapshotIdentityCheck: replacement, removal, shared invalidation, same-tick tokens, split and expiry passed");
  }
}
