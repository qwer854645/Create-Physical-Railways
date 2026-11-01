package com.guidedrailways.validation;

import com.guidedrailways.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Actual graph mutation and staged-index tests in the isolated server world. */
@GameTestHolder("guidedrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class TrackIndexServerTests {
  static Object field(Object owner, String name) throws Exception {
    var field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  @SuppressWarnings("unchecked")
  static Collection<TrackNetwork.Path> paths(GameTestHelper helper, UUID graph) throws Exception {
    return (Collection<TrackNetwork.Path>) JunctionServerTests.network("paths",
        new Class<?>[] {net.minecraft.server.level.ServerLevel.class, UUID.class}, helper.getLevel(), graph);
  }

  /** Inspect publication without forcing completion through the navigation query API. */
  @SuppressWarnings("unchecked")
  static Collection<TrackNetwork.Path> published(GameTestHelper helper, UUID graph) throws Exception {
    var storage = TrackNetwork.class.getDeclaredField("INDEX");
    storage.setAccessible(true);
    var index = ((Map<?, ?>) storage.get(null)).get(helper.getLevel());
    var slot = ((Map<?, ?>) field(index, "slots")).get(graph);
    var active = slot == null ? null : field(slot, "active");
    return active == null ? List.of() : ((Map<?, TrackNetwork.Path>) field(active, "paths")).values();
  }

  @GameTest(template = "empty", templateNamespace = "guidedrailways_signal_validation", timeoutTicks = 600)
  public static void stagedGraphUpdates(GameTestHelper helper) throws Exception {
    new Run(helper).start();
  }

  static final class Run {
    final GameTestHelper helper;
    final TrackGraph small = new TrackGraph(), large = new TrackGraph();
    TrackGraph replacement;
    TrackNode a, b, c, first, second;
    TrackEdge original;
    TrackNetwork.Path stable;
    int ticks, publishedAt = -1;
    boolean changedDuringBuild, audited;

    Run(GameTestHelper helper) { this.helper = helper; }

    void cleanup() {
      var graphs = Create.RAILWAYS.sided(helper.getLevel()).trackNetworks;
      graphs.remove(small.id);
      graphs.remove(large.id);
      TrackNetwork.refreshNow(helper.getLevel());
    }

    void start() throws Exception {
      var level = helper.getLevel();
      var graphs = Create.RAILWAYS.sided(level).trackNetworks;
      a = JunctionServerTests.node(small, level, 3072, 0);
      b = JunctionServerTests.node(small, level, 3072, 10);
      c = JunctionServerTests.node(small, level, 3074, 20);
      JunctionServerTests.connect(small, a, b);
      graphs.put(small.id, small);
      TrackNetwork.refreshNow(level);
      stable = paths(helper, small.id).iterator().next();
      helper.assertTrue(paths(helper, small.id).size() == 1, "small graph initially indexed");
      first = JunctionServerTests.node(large, level, 4096, 0);
      var previous = first;
      for (int i = 1; i <= 1024; i++) {
        var next = JunctionServerTests.node(large, level, 4096, i);
        JunctionServerTests.connect(large, previous, next);
        if (i == 1) second = next;
        previous = next;
      }
      original = large.getConnectionsFrom(first).get(second);
      graphs.put(large.id, large);
      helper.runAfterDelay(1, this::step);
    }

    void step() {
      try {
        ticks++;
        var level = helper.getLevel();
        TrackNetwork.refresh(level);
        var indexed = published(helper, large.id);
        helper.assertTrue(indexed.isEmpty() || indexed.size() == 1024,
            "initial build never exposes a partially scanned graph");
        if (ticks == 2 && indexed.isEmpty()) {
          large.putConnection(first, second, new TrackEdge(first, second, null, TrackMaterial.ANDESITE));
          changedDuringBuild = true;
        }
        if (indexed.size() == 1024 && publishedAt < 0) {
          helper.assertTrue(changedDuringBuild, "large graph required multiple budgeted ticks");
          helper.assertTrue(indexed.stream().noneMatch(p -> p.edge() == original),
              "in-progress changes restart staging rather than publishing stale edges");
          helper.assertTrue(paths(helper, small.id).iterator().next() == stable,
              "another graph's update preserves unrelated path identity");
          publishedAt = ticks;
          // Deliberately bypass putConnection's notification: compatibility audit must find it.
          small.getConnectionsFrom(b).put(c, new TrackEdge(b, c, null, TrackMaterial.ANDESITE));
        }
        if (publishedAt >= 0 && paths(helper, small.id).size() == 2 && !audited) {
          audited = true;
          small.getConnectionsFrom(a).remove(b);
          helper.assertTrue(!(boolean) JunctionServerTests.network("live",
              new Class<?>[] {net.minecraft.server.level.ServerLevel.class, TrackNetwork.Path.class}, level, stable),
              "unannounced removal immediately rejects old edges before an audit finishes");
          replacement = new TrackGraph(large.id);
          var x = JunctionServerTests.node(replacement, level, 4096, 0);
          var y = JunctionServerTests.node(replacement, level, 4096, 20);
          JunctionServerTests.connect(replacement, x, y);
          Create.RAILWAYS.sided(level).trackNetworks.put(large.id, replacement);
          // The next phase deliberately expects a different graph size.
          helper.runAfterDelay(2, this::finishReplacement);
          return;
        }
        helper.assertTrue(ticks < 500, "background audits complete within the fixture deadline");
        helper.runAfterDelay(1, this::step);
      } catch (Throwable error) {
        cleanup();
        org.slf4j.LoggerFactory.getLogger("guidedrailways.index-validation").error("INDEX_FIXTURE_FAILED", error);
        helper.fail(error.toString());
      }
    }

    void finishReplacement() {
      try {
        TrackNetwork.refresh(helper.getLevel());
        var paths = paths(helper, large.id);
        helper.assertTrue(paths.size() == 1 && paths.iterator().next().network() == replacement,
            "same UUID graph replacement switches to the new graph instance");
        cleanup();
        helper.assertTrue(paths(helper, large.id).isEmpty() && paths(helper, small.id).isEmpty(),
            "graph removal discards active query paths");
        org.slf4j.LoggerFactory.getLogger("guidedrailways.index-validation")
            .info("TRACK_INDEX_PASS edges=1024 initialBuildTicks={} auditTicks={}", publishedAt, ticks - publishedAt);
        helper.succeed();
      } catch (Throwable error) {
        cleanup();
        helper.fail(error.toString());
      }
    }
  }
}
