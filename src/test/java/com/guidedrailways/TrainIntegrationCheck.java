package com.guidedrailways;

import java.util.*;
import org.joml.Vector3d;

public final class TrainIntegrationCheck {
  private static int checks;

  private static UUID id(int n) {
    return new UUID(0, n);
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var random = new Random(271828);
    for (int trial = 0; trial < 250; trial++) {
      int n = 1 + random.nextInt(24);
      var ids = new ArrayList<UUID>();
      for (int i = 0; i < n; i++) ids.add(id(100 + i));
      boolean[][] reach = new boolean[n][n];
      for (int i = 0; i < n; i++) reach[i][i] = true;
      var edges = new ArrayList<ConsistGraph.Link>();
      for (int i = 0; i < n * 2; i++) {
        int x = random.nextInt(n), y = random.nextInt(n);
        if (random.nextBoolean()) {
          edges.add(new ConsistGraph.Link(ids.get(x), ids.get(y)));
          reach[x][y] = reach[y][x] = true;
        }
      }
      for (int k = 0; k < n; k++)
        for (int i = 0; i < n; i++)
          for (int j = 0; j < n; j++) reach[i][j] |= reach[i][k] && reach[k][j];
      var groups = ConsistGraph.components(ids, edges);
      var buckets = ConsistGraph.bucket(groups, edges, ConsistGraph.Link::a);
      for (int i = 0; i < groups.size(); i++) {
        var members = groups.get(i);
        check(
            buckets.get(i).equals(edges.stream().filter(e -> members.contains(e.a())).toList()),
            "linear bucketing retains every consist edge and original processing order");
      }
      var owner = new HashMap<UUID, Set<UUID>>();
      for (var group : groups)
        for (var member : group)
          check(
              owner.put(member, group) == null, "every body belongs to exactly one shared consist");
      for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++)
          check(
              owner.get(ids.get(i)).contains(ids.get(j)) == reach[i][j],
              "shared graph partition matches independent transitive closure, including loops and bare bodies");
      Collections.shuffle(ids, random);
      Collections.shuffle(edges, random);
      check(
          groups.equals(ConsistGraph.components(ids, edges)),
          "component identity is independent of block load and edge iteration order");
    }
    var a = new ConsoleElection.Console(id(1), id(11), 10);
    var b = new ConsoleElection.Console(id(2), id(12), 20);
    var c = new ConsoleElection.Console(id(3), id(11), 30);
    var joined =
        List.of(new ConsoleElection.Link(id(11), id(90)), new ConsoleElection.Link(id(90), id(12)));
    for (int i = 0; i < 100; i++) {
      var records = new ArrayList<>(List.of(a, b, c));
      Collections.shuffle(records, new Random(i));
      var election = ConsoleElection.elect(records, joined);
      check(
          election.get(id(11)).equals(a.id())
              && election.get(id(12)).equals(a.id())
              && election.get(id(90)).equals(a.id()),
          "placement order wins regardless of load order or bare intermediate carriers");
      var split = ConsoleElection.elect(records, List.of());
      check(
          split.get(id(11)).equals(a.id()) && split.get(id(12)).equals(b.id()),
          "uncoupling elects the oldest console independently in each new train");
    }
    check(
        ConsoleElection.elect(List.of(b, c), joined).get(id(11)).equals(b.id()),
        "removing the primary promotes the next oldest console across carriages");
    var tied = new ConsoleElection.Console(id(8), id(12), 0);
    var tied2 = new ConsoleElection.Console(id(7), id(11), 0);
    check(
        ConsoleElection.elect(List.of(a, tied, tied2), joined).get(id(12)).equals(tied2.id()),
        "equal placement orders use stable persisted UUID tie-breaking");
    var reload =
        new ConsoleElection.Console(
            UUID.fromString(a.id().toString()),
            UUID.fromString(a.carrier().toString()),
            Long.parseLong("10"));
    check(
        ConsoleElection.elect(List.of(reload, b), joined).get(id(12)).equals(a.id()),
        "serializable identity/order survive save and reload");
    check(
        ConsoleElection.elect(List.of(), joined).isEmpty(),
        "a train without consoles has no invented authority");
    var presence = new StationStopState();
    for (int i = 0; i < 5; i++)
      check(!presence.tick(.15, .05, true), "transient arrival must not power the comparator");
    check(presence.tick(.15, .05, true), "six stable ticks declare arrival");
    check(
        presence.tick(.35, .15, true), "small contact/suspension jitter does not flicker arrival");
    check(!presence.tick(.35, .3, true), "departure clears arrival");
    for (int i = 0; i < 10; i++)
      check(!presence.tick(0, 4, true), "passing a station at speed never reports arrival");
    for (int i = 0; i < 6; i++) presence.tick(0, 0, true);
    check(
        !presence.tick(0, 0, false),
        "removed station, broken track or missing train clears the output");
    for (int i = 0; i < 10; i++)
      check(!presence.tick(2, 0, true), "blocked short of station never reports arrival");
    check(!presence.tick(Double.NaN, 0, true), "invalid geometry cannot leave a ghost arrival");
    for (double yaw : new double[] {-Math.PI, -Math.PI / 2, -.7, 0, .7, Math.PI / 2, Math.PI})
      for (double pitch : new double[] {-.3, 0, .3})
        for (double length : new double[] {.73, 1.6, 3.8})
          for (double lock : new double[] {0, 1}) {
            var pivot = new Vector3d(.5, .5, .75);
            var direction = new Vector3d(0, 0, 1).rotateX(pitch).rotateY(yaw);
            var layout =
                CouplerVisual.layout(
                    pivot,
                    new Vector3d(pivot).fma(length, direction),
                    new Vector3d(0, 1, 0),
                    lock,
                    .73);
            var shapes = CouplerLivingGeometry.boxes(layout);
            check(
                shapes.size() <= 256,
                "animated living collision stays bounded without additional bodies");
            for (var hardware : layout.boxes())
              for (double x :
                  new double[] {hardware.x0(), (hardware.x0() + hardware.x1()) * .5, hardware.x1()})
                for (double y : new double[] {hardware.y0(), hardware.y1()})
                  for (int k = 0; k <= 10; k++) {
                    var p =
                        new Vector3d(x, y, hardware.z0() + (hardware.z1() - hardware.z0()) * k / 10)
                            .rotate(layout.rotation())
                            .add(pivot);
                    check(
                        shapes.stream().anyMatch(box -> box.contains(p)),
                        "rotated drawbar and hook hardware must have living collision");
                  }
            check(
                shapes.stream().noneMatch(box -> box.contains(new Vector3d(20, 20, 20))),
                "animated collision does not create an oversized world barrier");
          }
    System.out.println("TrainIntegrationCheck: " + checks + " assertions passed");
  }
}
