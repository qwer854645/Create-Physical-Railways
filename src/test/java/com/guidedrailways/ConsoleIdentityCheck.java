package com.guidedrailways;

import java.util.*;

public final class ConsoleIdentityCheck {
  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var ids = new ConsoleIdentities<Object>();
    var original = new Object();
    var copy = new Object();
    var live = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    live.add(original);
    live.add(copy);
    var persisted = UUID.randomUUID();
    check(
        ids.claim(persisted, original, live::contains).equals(persisted),
        "first console keeps persisted identity");
    var copied = ids.claim(persisted, copy, live::contains);
    check(!copied.equals(persisted), "loaded clone receives a fresh identity");
    check(ids.claim(copied, copy, live::contains).equals(copied), "re-registration is stable");
    var bodyA = UUID.randomUUID();
    var bodyB = UUID.randomUUID();
    var chosen =
        ConsoleElection.electGroups(
            List.of(
                new ConsoleElection.Console(persisted, bodyA, 1),
                new ConsoleElection.Console(copied, bodyB, 2)),
            List.of(Set.of(bodyA), Set.of(bodyB)));
    check(
        chosen.get(bodyA).equals(persisted) && chosen.get(bodyB).equals(copied),
        "independent trains choose their own consoles");
    var coupled =
        ConsoleElection.electGroups(
            List.of(
                new ConsoleElection.Console(persisted, bodyA, 1),
                new ConsoleElection.Console(copied, bodyB, 2)),
            List.of(Set.of(bodyA, bodyB)));
    check(coupled.get(bodyB).equals(persisted), "coupled train keeps its oldest console");
    live.remove(original);
    var reloaded = new Object();
    live.add(reloaded);
    check(
        ids.claim(persisted, reloaded, live::contains).equals(persisted),
        "unloaded owner does not change reload identity");
    ids.release(original);
    check(
        !ids.claim(persisted, new Object(), live::contains).equals(persisted),
        "late old-owner cleanup cannot release replacement identity");
    ids.release(reloaded);
    check(
        ids.claim(persisted, new Object(), live::contains).equals(persisted),
        "explicit unload releases identity");
    System.out.println(
        "ConsoleIdentityCheck: clone, re-registration, election, reload and late cleanup passed");
  }
}
