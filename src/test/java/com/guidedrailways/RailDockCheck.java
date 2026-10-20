package com.guidedrailways;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

public final class RailDockCheck {
  private static int checks;

  private static void check(boolean value, String why) {
    checks++;
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var lease = new RailDockLease();
    check(!lease.active(0), "freshly loaded connectors have no automatic lock");
    check(
        lease.claim(first, 10) && lease.active(49) && !lease.active(50),
        "automatic lock has a bounded heartbeat lifetime");
    check(!lease.claim(second, 20), "another train cannot steal an active connector");
    check(lease.claim(first, 30) && lease.active(69), "same train refreshes its docking heartbeat");
    check(
        !lease.release(second, 40) && lease.active(40),
        "another controller cannot release this train's docking lock");
    check(
        lease.release(first, 40) && !lease.active(40),
        "departure releases the automatic request immediately");
    check(
        !lease.claim(first, 59) && lease.claim(second, 60),
        "release allows native undocking to settle before reuse");
    check(
        !lease.claim(first, 99) && lease.claim(first, 100),
        "expired heartbeat releases a connector after controller removal");
    check(
        !lease.release(second, 101) && lease.active(101),
        "late release from a previous owner cannot unlock a new arrival");
    lease.clear(102);
    check(
        !lease.active(102) && !lease.canClaim(first, 103),
        "rebinding clears the active request and observes release cooldown");
    check(
        RailDockMail.allows(true, true, true, true, true),
        "unload matching mail at the linked station");
    check(
        !RailDockMail.allows(true, true, true, true, false),
        "never unload a package addressed to another station");
    check(
        !RailDockMail.allows(true, true, false, true, false),
        "an unbound station or one without mailboxes rejects packages");
    check(
        RailDockMail.allows(true, true, true, false, false),
        "load outgoing mail for another address");
    check(
        !RailDockMail.allows(true, true, true, false, true),
        "do not reload mail already delivered to the current station");
    for (boolean shore : new boolean[] {false, true})
      for (boolean address : new boolean[] {false, true}) {
        check(
            !RailDockMail.allows(true, false, true, shore, address),
            "unlocked or nonreciprocal pairs cannot transfer packages");
        check(
            !RailDockMail.allows(true, true, false, shore, address),
            "invalid station identity cannot transfer packages");
        check(
            RailDockMail.allows(false, false, false, shore, address),
            "ordinary items keep native insertion permissions");
      }
    var station = UUID.randomUUID();
    var car = UUID.randomUUID();
    var trailer = UUID.randomUUID();
    var stranger = UUID.randomUUID();
    for (boolean reciprocal : new boolean[] {false, true})
      for (boolean a : new boolean[] {false, true})
        for (boolean b : new boolean[] {false, true})
          check(
              RailDockGate.ready(station, station, car, Set.of(car, trailer), reciprocal, a, b)
                  == (reciprocal && a && b),
              "both native endpoints must be mutually paired and fully locked");
    check(
        RailDockGate.ready(station, station, trailer, Set.of(car, trailer), true, true, true),
        "a dock on a coupled trailer serves the whole consist");
    check(
        !RailDockGate.ready(station, station, stranger, Set.of(car, trailer), true, true, true),
        "a nearby different train cannot exchange this train's mail");
    check(
        !RailDockGate.ready(station, UUID.randomUUID(), car, Set.of(car), true, true, true),
        "a dock linked to another station cannot transfer packages");
    check(
        !RailDockGate.ready(station, station, null, Set.of(car), true, true, true),
        "world-to-world docking is not a train endpoint");
    var states =
        JsonParser.parseString(
                Files.readString(
                    Path.of(
                        "src/main/resources/assets/guidedrailways/blockstates/rail_docking_connector.json")))
            .getAsJsonObject()
            .getAsJsonObject("variants");
    check(states.size() == 24, "all native facing, power and extension states have a model");
    for (var entry : states.entrySet())
      check(
          entry
              .getValue()
              .getAsJsonObject()
              .get("model")
              .getAsString()
              .startsWith("simulated:block/docking_connector/"),
          "reuse native models for every state");
    var recipe =
        JsonParser.parseString(
                Files.readString(
                    Path.of(
                        "src/main/resources/data/guidedrailways/recipe/rail_docking_connector.json")))
            .getAsJsonObject();
    check(
        recipe
            .getAsJsonArray("neoforge:conditions")
            .get(0)
            .getAsJsonObject()
            .get("modid")
            .getAsString()
            .equals("simulated"),
        "optional recipe must not resolve a missing native connector");
    System.out.println("RailDockCheck: " + checks + " assertions passed");
  }
}
