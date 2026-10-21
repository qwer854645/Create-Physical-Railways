package com.guidedrailways.validation;

import com.guidedrailways.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.signal.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.*;

/** Loaded-server signal API fixtures, not a claim of physically moving trains on a test track. */
@Mod("guidedrailways_signal_validation")
@GameTestHolder("guidedrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class SignalServerTests {
  private static Object field(Object object, String name) throws Exception {
    var f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  private static void project(Object state, Set<UUID> occupied, Set<UUID> claimed)
      throws Exception {
    var method = state.getClass().getDeclaredMethod("update", Set.class, Set.class);
    method.setAccessible(true);
    method.invoke(state, occupied, claimed);
  }

  private static void clear(Object state) throws Exception {
    var method = state.getClass().getDeclaredMethod("clear");
    method.setAccessible(true);
    method.invoke(state);
  }

  @GameTest(
      template = "empty",
      templateNamespace = "guidedrailways_signal_validation",
      timeoutTicks = 100)
  public static void mixedSignalQueries(GameTestHelper helper) throws Exception {
    var manager = Create.RAILWAYS;
    var proxyConstructor = PhysicalTrainProxy.class.getDeclaredConstructor(UUID.class);
    proxyConstructor.setAccessible(true);
    var physical = proxyConstructor.newInstance(UUID.randomUUID());
    var signals = field(physical, "signals");
    var nativeTrain =
        new Train(
            UUID.randomUUID(),
            null,
            null,
            new ArrayList<>(),
            new ArrayList<>(),
            true,
            Component.literal("Native signal fixture"),
            TrainIconType.getDefault(),
            0);
    var first = new SignalEdgeGroup(UUID.randomUUID());
    var next = new SignalEdgeGroup(UUID.randomUUID());
    var crossing = new SignalEdgeGroup(UUID.randomUUID());
    var owner = UUID.randomUUID();
    var other = UUID.randomUUID();
    var boundary = UUID.randomUUID();
    var statesField = PhysicalSignals.class.getDeclaredField("STATES");
    statesField.setAccessible(true);
    @SuppressWarnings("unchecked")
    var states = (Map<Object, Object>) statesField.get(null);
    var type = Class.forName("com.guidedrailways.PhysicalSignals$State");
    var constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    var state = constructor.newInstance();
    var previous = states.put(helper.getLevel(), state);
    var ledger = (SignalOccupancy) field(state, "ledger");
    @SuppressWarnings("unchecked")
    var identities = (Map<UUID, UUID>) field(state, "identities");
    identities.put(physical.id, owner);
    int checks = 0;
    try {
      manager.trains.put(physical.id, physical);
      for (var group : List.of(first, next, crossing))
        manager.signalEdgeGroups.put(group.id, group);
      ledger.refresh(Map.of(owner, Set.of(first.id)), Set.of(owner), 0);
      project(signals, Set.of(first.id), Set.of());
      helper.assertTrue(
          first.isOccupiedUnless(nativeTrain), "native train sees a physical occupant");
      checks++;
      helper.assertTrue(
          !first.isOccupiedUnless(physical), "physical train excludes its own native identity");
      checks++;
      helper.assertTrue(
          first.trains.contains(physical), "section stores the exact registered physical proxy");
      checks++;
      first.trains.add(nativeTrain);
      helper.assertTrue(
          first.isOccupiedUnless(physical), "real native occupant remains visible beside self");
      checks++;
      helper.assertTrue(
          !ledger.claim(
              owner,
              Map.of(first.id, boundary),
              0,
              id ->
                  manager.signalEdgeGroups.get(id).trains.stream()
                      .anyMatch(t -> !(t instanceof PhysicalTrainProxy))),
          "physical claim is rejected by a native occupant");
      checks++;
      first.trains.remove(nativeTrain);
      ledger.refresh(Map.of(owner, Set.of(first.id, next.id)), Set.of(owner), 1);
      project(signals, Set.of(first.id, next.id), Set.of());
      helper.assertTrue(
          first.isOccupiedUnless(nativeTrain) && next.isOccupiedUnless(nativeTrain),
          "tail spanning two sections holds both");
      checks++;
      ledger.refresh(Map.of(owner, Set.of(next.id)), Set.of(owner), 2);
      project(signals, Set.of(next.id), Set.of());
      helper.assertTrue(
          !first.isOccupiedUnless(nativeTrain) && next.isOccupiedUnless(nativeTrain),
          "tail release clears only the old section");
      checks++;
      first.intersectingResolved.add(first);
      first.intersectingResolved.add(crossing);
      crossing.trains.add(nativeTrain);
      helper.assertTrue(
          first.isOccupiedUnless(physical), "native crossing conflict blocks physical train");
      checks++;
      crossing.trains.clear();
      helper.assertTrue(
          ledger.claim(owner, Map.of(first.id, boundary), 2, id -> false),
          "physical backend can claim a free future section");
      checks++;
      project(signals, Set.of(next.id), ledger.claimedBy(owner));
      helper.assertTrue(
          first.isOccupiedUnless(nativeTrain) && !first.isOccupiedUnless(physical),
          "future physical claim blocks others but not its owner");
      checks++;
      helper.assertTrue(
          !ledger.claim(other, Map.of(first.id, UUID.randomUUID()), 2, id -> false),
          "opposing physical request cannot acquire the same section");
      checks++;
      next.trains.clear();
      physical.earlyTick(helper.getLevel());
      helper.assertTrue(
          next.trains.contains(physical),
          "native early tick republishes after the manager clears sections");
      checks++;
      next.trains.add(nativeTrain);
      clear(signals);
      helper.assertTrue(
          !next.trains.contains(physical) && next.trains.contains(nativeTrain),
          "cleanup preserves another train's record");
      checks++;
      helper.assertTrue(
          physical.occupiedSignalBlocks.isEmpty() && physical.reservedSignalBlocks.isEmpty(),
          "cleanup clears query fields");
      checks++;
      org.slf4j.LoggerFactory.getLogger("guidedrailways.signal-validation")
          .info("MIXED_SIGNAL_API_PASS checks={}", checks);
      helper.succeed();
    } finally {
      clear(signals);
      manager.trains.remove(physical.id);
      for (var group : List.of(first, next, crossing))
        manager.signalEdgeGroups.remove(group.id, group);
      if (previous == null) states.remove(helper.getLevel());
      else states.put(helper.getLevel(), previous);
    }
  }
}
