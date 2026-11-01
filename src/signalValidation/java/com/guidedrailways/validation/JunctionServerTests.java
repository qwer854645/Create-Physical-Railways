package com.guidedrailways.validation;

import com.guidedrailways.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.*;

/** Real server graph/projection integration; not a moving rigid-body or laid-track fixture. */
@GameTestHolder("guidedrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class JunctionServerTests {
  static TrackNode node(TrackGraph graph, ServerLevel level, double x, double z) {
    var node = new TrackNode(new TrackNodeLocation(x, 90, z).in(level), TrackGraph.nextNodeId(), new Vec3(0, 1, 0));
    graph.addNode(node);
    return node;
  }

  static void connect(TrackGraph graph, TrackNode a, TrackNode b) {
    graph.putConnection(a, b, new TrackEdge(a, b, null, TrackMaterial.ANDESITE));
    graph.putConnection(b, a, new TrackEdge(b, a, null, TrackMaterial.ANDESITE));
  }

  static Object network(String name, Class<?>[] types, Object... args) throws Exception {
    var method = TrackNetwork.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  static TrackNetwork.Sample follow(ServerLevel level, Vector3d point, Vector3d heading,
      TrackNetwork.Sample previous, TrackNetwork.Sample leader, Policy policy) throws Exception {
    return (TrackNetwork.Sample) network("follow", new Class<?>[] {ServerLevel.class,
        Vector3dc.class, Vector3dc.class, TrackNetwork.Sample.class, TrackNetwork.Sample.class,
        double.class, boolean.class, Class.forName("com.guidedrailways.TrackNetwork$RoutePolicy")},
        level, point, heading, previous, leader, .8, false, policy.callback());
  }

  static final class Policy {
    final RouteMemory memory;
    final RouteMemory.Axle axle;
    final long now;
    int wanted, commits;
    boolean allowed;

    Policy(RouteMemory memory, RouteMemory.Axle axle, int wanted, long now) {
      this.memory = memory;
      this.axle = axle;
      this.wanted = wanted;
      this.now = now;
    }

    public int choose(TrackNetwork.Path path, int node, int from, int[] exits, int proposed, Vector3d position) {
      if (Arrays.stream(exits).noneMatch(e -> e == wanted)) return -1;
      return memory.preview(axle, new RouteMemory.Junction(path.graph(), node), from, exits, wanted, true, now);
    }

    public boolean commit(TrackNetwork.Path path, int node, int from, int[] exits, int selected, Vector3d position) {
      commits++;
      return memory.commit(axle, new RouteMemory.Junction(path.graph(), node), from, exits, selected, true, now) == selected;
    }

    public boolean mayPass(TrackNetwork.Path path, int node, int from, int to) { return allowed; }
    public boolean blocked() { return !allowed; }
    public void observe(Vector3dc position) {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    Object callback() throws Exception {
      var type = Class.forName("com.guidedrailways.TrackNetwork$RoutePolicy");
      return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
          (proxy, method, args) -> switch (method.getName()) {
            case "choose" -> choose((TrackNetwork.Path) args[0], (int) args[1], (int) args[2],
                (int[]) args[3], (int) args[4], (Vector3d) args[5]);
            case "commit" -> commit((TrackNetwork.Path) args[0], (int) args[1], (int) args[2],
                (int[]) args[3], (int) args[4], (Vector3d) args[5]);
            case "mayPass" -> allowed;
            case "blocked" -> !allowed;
            case "steer" -> Enum.valueOf((Class) Class.forName("com.guidedrailways.BranchSelect$Steer"), "NONE");
            case "gauge" -> Enum.valueOf((Class) Class.forName("com.guidedrailways.RailGauge"), "STANDARD");
            case "observe", "leader" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
          });
    }
  }

  @GameTest(template = "empty", templateNamespace = "guidedrailways_signal_validation", timeoutTicks = 100)
  @SuppressWarnings("unchecked")
  public static void plannedBranchesAndBlockedRecovery(GameTestHelper helper) throws Exception {
    var level = helper.getLevel();
    var graph = new TrackGraph();
    var manager = Create.RAILWAYS.sided(level);
    int checks = 0;
    try {
      for (int x = 127; x <= 128; x++)
        for (int z = -1; z <= 0; z++) level.getChunk(x, z);
      var from = node(graph, level, 2048, -10);
      var junction = node(graph, level, 2048, 0);
      var left = node(graph, level, 2046, 10);
      var right = node(graph, level, 2050, 10);
      connect(graph, from, junction);
      connect(graph, junction, left);
      connect(graph, junction, right);
      manager.trackNetworks.put(graph.id, graph);
      TrackNetwork.refresh(level);
      var paths = (Collection<TrackNetwork.Path>) network("paths", new Class<?>[] {ServerLevel.class, UUID.class}, level, graph.id);
      var incoming = paths.stream()
          .filter(p -> p.a() == from.getNetId() && p.b() == junction.getNetId()).findFirst().orElseThrow();
      var heading = new Vector3d(0, 0, 1);
      var previous = new TrackNetwork.Sample(incoming, .98, new Vector3d(2048, 90, -.2), heading, new Vector3d(0, 1, 0));
      var point = new Vector3d(2048, 90, .5);
      var head = new RouteMemory.Axle(UUID.randomUUID(), 0);
      var tail = new RouteMemory.Axle(head.bogie(), 1);
      var memory = new RouteMemory(Set.of(head, tail));
      for (int lap = 0; lap < 8; lap++) {
        int wanted = (lap % 2 == 0 ? left : right).getNetId();
        int other = (lap % 2 == 0 ? right : left).getNetId();
        var policy = new Policy(memory, head, wanted, level.getGameTime());
        for (int retry = 0; retry < 3; retry++) {
          var denied = follow(level, point, heading, previous, null, policy);
          helper.assertTrue(denied == previous && policy.commits == 0,
              "denied crossing retains incoming edge and never commits another branch");
          checks++;
        }
        policy.allowed = true;
        var selected = follow(level, point, heading, previous, null, policy);
        helper.assertTrue(selected != null && (selected.path().a() == wanted || selected.path().b() == wanted),
            "released crossing selects the planned exit on each alternating lap");
        var following = new Policy(memory, tail, other, level.getGameTime());
        following.allowed = true;
        var followed = follow(level, point, heading, previous, selected, following);
        helper.assertTrue(followed != null && followed.path().equals(selected.path()),
            "tail follows committed passage despite another proposed exit");
        helper.assertTrue(memory.active(level.getGameTime()).isEmpty(), "fully cleared crossing releases its hold");
        memory.clearRepeat(head);
        memory.clearRepeat(tail);
        checks += 3;
      }
      org.slf4j.LoggerFactory.getLogger("guidedrailways.junction-validation")
          .info("JUNCTION_GRAPH_PASS laps=8 checks={}", checks);
      helper.succeed();
    } finally {
      manager.trackNetworks.remove(graph.id);
      TrackNetwork.refresh(level);
    }
  }
}
