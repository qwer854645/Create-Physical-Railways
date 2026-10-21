package com.guidedrailways;

import java.util.*;

public final class AutomaticScheduleCheck {
  private static int checks;

  private static void check(boolean ok, String reason) {
    checks++;
    if (!ok) throw new AssertionError(reason);
  }

  private static ScheduleRoute.Edge e(int a, int b) {
    return new ScheduleRoute.Edge(a, b);
  }

  private static ScheduleRoute.Goal goal(int a, int b, double offset) {
    return new ScheduleRoute.Goal(UUID.randomUUID(), "Station", e(a, b), offset);
  }

  private static final class Network implements ScheduleRoute.Network {
    final Map<ScheduleRoute.Edge, Double> lengths = new HashMap<>();
    final Map<ScheduleRoute.Edge, List<ScheduleRoute.Edge>> turns = new HashMap<>();

    Network edge(int a, int b, double length) {
      lengths.put(e(a, b), length);
      return this;
    }

    Network turn(int a, int b, int c) {
      turns.computeIfAbsent(e(a, b), k -> new ArrayList<>()).add(e(b, c));
      return this;
    }

    @Override
    public double length(ScheduleRoute.Edge edge) {
      return lengths.getOrDefault(edge, Double.POSITIVE_INFINITY);
    }

    @Override
    public List<ScheduleRoute.Edge> exits(ScheduleRoute.Edge edge) {
      return turns.getOrDefault(edge, List.of());
    }
  }

  public static void main(String[] args) {
    var n =
        new Network()
            .edge(1, 2, 10)
            .edge(2, 3, 20)
            .edge(2, 4, 5)
            .edge(4, 3, 5)
            .edge(3, 5, 10)
            .turn(1, 2, 3)
            .turn(1, 2, 4)
            .turn(2, 4, 3)
            .turn(4, 3, 5)
            .turn(2, 3, 5);
    var route = ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(3, 5, 3)), 0);
    check(
        route != null && Math.abs(route.distance() - 17) < 1e-9,
        "shortest allowed route includes both partial edge distances");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(3, 5, 3)), 0, 16) == null,
        "short footprint searches cannot wander around a distant loop");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(3, 5, 3)), 0, 17).distance() == 17,
        "bounded footprint tracing includes its exact distance limit");
    check(
        route.edges().equals(List.of(e(1, 2), e(2, 4), e(4, 3), e(3, 5))),
        "junction is selected by destination, not turn angle");
    check(route.next(1, 2, 0) == 4, "route exposes the required fork exit");
    check(
        Math.abs(route.remaining(n, 1, 2) - 11) < 1e-9,
        "remaining distance updates without replanning or nearest-station distance");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(1, 2, 8)), 0).distance() == 2,
        "same-edge station ahead");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(1, 2, 5)), 0) == null,
        "no arrival at a station already passed without a return route");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(2, 1, 2)), 0) == null,
        "wrong station approach direction is not accepted");
    check(
        ScheduleRoute.find(n, e(1, 2), 6, List.of(goal(2, 3, 4), goal(2, 4, 1)), 0)
            .goal()
            .edge()
            .equals(e(2, 4)),
        "nearest reachable duplicate-name station wins");
    var loop =
        new Network()
            .edge(1, 2, 10)
            .edge(2, 3, 10)
            .edge(3, 1, 10)
            .turn(1, 2, 3)
            .turn(2, 3, 1)
            .turn(3, 1, 2);
    var back = ScheduleRoute.find(loop, e(1, 2), 7, List.of(goal(1, 2, 3)), 0);
    check(
        back != null && back.distance() == 26 && back.edges().size() == 4,
        "a passed station on the starting edge can be reached after one circuit");
    var docked = ScheduleRoute.find(loop, e(1, 2), 3.08, List.of(goal(1, 2, 3)), .25);
    check(
        docked != null && docked.distance() == 0 && docked.edges().size() == 1,
        "resuming a saved wait just past the marker must not schedule a complete circuit");
    check(
        Math.abs(docked.remaining(loop, 0, 3.08) + .08) < 1e-9,
        "arrival tolerance preserves the measured signed stop error");
    check(back.next(3, 1, 2) == 2, "loop route preserves repeated-edge transitions");
    check(
        ScheduleRoute.find(loop, e(1, 2), Double.NaN, List.of(goal(1, 2, 3)), 0) == null,
        "invalid location is rejected");
    var progressRoute =
        new ScheduleRoute.Route(
            List.of(e(1, 2), e(2, 3), e(3, 4), e(4, 5), e(5, 6)), goal(5, 6, 1), 5);
    check(
        progressRoute.progress(e(4, 5), 0) == 3,
        "several short graph edges may be crossed between navigation ticks");
    check(
        progressRoute.progress(e(3, 4), 3) == 2,
        "a one-edge publication delay remains on the same planned route");
    check(
        progressRoute.progress(e(2, 9), 1) == -1,
        "an adjacent wrong branch is never accepted as navigation progress");
    check(
        progressRoute.progress(e(5, 4), 3) == -1,
        "opposite travel direction is not silently accepted");
    check(
        progressRoute.next(1, 2, 0) == 3 && progressRoute.next(2, 9, 0) == -1,
        "junction lookup returns only the planned exit");
    var a = new UUID(0, 1);
    var b = new UUID(0, 2);
    var middle = new UUID(0, 3);
    for (int direction : new int[] {-1, 1})
      for (int outA : new int[] {-1, 1})
        for (int outB : new int[] {-1, 1}) {
          var signs =
              ConsistDirection.orient(
                  a, direction, List.of(new ConsistDirection.Link(a, b, outA, outB)));
          check(
              signs.get(b) == -direction * outA * outB,
              "front/front and rear/front couplings map traction to consistent local directions");
          check(
              signs.get(a) * outA == -signs.get(b) * outB,
              "neighbouring cars travel consistently at their coupling sockets");
        }
    check(
        ConsistDirection.orient(
                    a,
                    1,
                    List.of(
                        new ConsistDirection.Link(a, middle, 1, -1),
                        new ConsistDirection.Link(middle, b, 1, 1)))
                .get(b)
            == -1,
        "carriers without bogies propagate orientation");
    check(
        ConsistDirection.orient(
                a,
                1,
                List.of(
                    new ConsistDirection.Link(a, b, 1, -1), new ConsistDirection.Link(a, b, 1, 1)))
            == null,
        "contradictory coupling orientation cannot silently drive cars against each other");
    for (double dt : new double[] {.05, 1d / 120})
      for (double mass : new double[] {80, 600, 5000})
        for (double rpm : new double[] {32, 256})
          for (double length : new double[] {3, 20, 120}) {
            double speed = 0, x = 0;
            AutomaticDriveModel.Demand demand = null;
            double nextControl = 0;
            boolean arrived = false;
            for (int step = 0; step < 1200 / dt; step++) {
              double time = step * dt, remaining = length - x;
              if (AutomaticDriveModel.arrived(remaining, Math.abs(speed))) {
                arrived = true;
                break;
              }
              if (time + 1e-9 >= nextControl) {
                demand = AutomaticDriveModel.demand(remaining, speed, rpm * .05, 4);
                nextControl = time + .05;
              }
              double force =
                  DriverModel.force(
                      speed,
                      demand.ceiling(),
                      rpm,
                      mass,
                      demand.notch(),
                      false,
                      false,
                      6,
                      2.5,
                      4,
                      .6,
                      dt,
                      mass,
                      false);
              double next = speed + force / mass * dt;
              check(
                  Double.isFinite(next) && Math.abs(next - speed) <= 4 * dt + 1e-8,
                  "automatic driving obeys the existing force and acceleration bounds");
              x += (speed + next) * .5 * dt;
              speed = next;
              check(
                  x <= length + .25, "conservative braking does not overrun the station tolerance");
            }
            check(
                arrived,
                "stop profile reaches a stationary arrival across speeds, loads, distances and physics steps");
          }
    check(
        !AutomaticDriveModel.arrived(2, 0), "being blocked short of the station is not an arrival");
    check(
        !AutomaticDriveModel.arrived(0, 3),
        "passing the station at speed does not complete the entry");
    check(AutomaticDriveModel.demand(30, 6, 0, 4).notch() == -4, "loss of power requests braking");
    check(
        AutomaticDriveModel.demand(-1, 2, 12.8, 4).notch() == -4,
        "overshoot never requests forward acceleration");
    System.out.println("AutomaticScheduleCheck: " + checks + " assertions passed");
  }
}
