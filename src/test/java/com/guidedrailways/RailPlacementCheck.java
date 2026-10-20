package com.guidedrailways;

import java.util.List;
import org.joml.Vector3d;

public final class RailPlacementCheck {
  static int checks;

  static void check(boolean ok, String text) {
    checks++;
    if (!ok) throw new AssertionError(text);
  }

  static RailPlacement.Route circle(double radius) {
    return d -> {
      double a = d / radius;
      return new RailPlacement.Point(
          new Vector3d(radius * (1 - Math.cos(a)), 0, radius * Math.sin(a)),
          new Vector3d(Math.sin(a), 0, Math.cos(a)),
          new Vector3d(0, 1, 0));
    };
  }

  static RailPlacement.Route straight(double limit) {
    return d ->
        Math.abs(d) > limit
            ? null
            : new RailPlacement.Point(
                new Vector3d(0, 0, d), new Vector3d(0, 0, 1), new Vector3d(0, 1, 0));
  }

  static void fitChecks(RailPlacement.Route route, RailPlacement.Bogie b) {
    var front = new Vector3d(0, -BogieGeometry.RIDE_HEIGHT, 1).rotate(b.rotation()).add(b.origin());
    var rear = new Vector3d(0, -BogieGeometry.RIDE_HEIGHT, -1).rotate(b.rotation()).add(b.origin());
    check(
        front.distance(route.at(b.front()).position()) < 1e-5,
        "front axle fits actual rail position");
    check(
        rear.distance(route.at(b.rear()).position()) < 1e-5, "rear axle fits actual rail position");
    check(Math.abs(front.distance(rear) - 2) < 1e-9, "wheelbase stays rigid");
    check(b.rotation().isFinite() && b.origin().isFinite(), "finite bogie pose");
  }

  public static void main(String[] args) {
    for (double r : new double[] {2, 4, 8, 16, 64})
      for (double centre : new double[] {-3, 0, 3}) {
        var route = circle(r);
        var fit = RailPlacement.bogie(route, centre, BogieGeometry.RIDE_HEIGHT, 1);
        fitChecks(route, fit);
        check(fit.front() - centre > 1, "arc length exceeds half chord on a bend");
      }
    var straight = straight(30);
    fitChecks(straight, RailPlacement.bogie(straight, 0, BogieGeometry.RIDE_HEIGHT, 1));
    fitChecks(straight(1), RailPlacement.bogie(straight(1), 0, BogieGeometry.RIDE_HEIGHT, 1));
    for (double r : new double[] {4, 8, 16, 64})
      for (double spacing : new double[] {2, 4, 6})
        for (int selected : new int[] {0, 1}) {
          var route = circle(r);
          var seats = List.of(new Vector3d(0, .25, 0), new Vector3d(0, .25, spacing));
          var plan =
              RailPlacement.carriage(
                  route, seats, selected, new Vector3d(0, 0, 1), BogieGeometry.RIDE_HEIGHT, 1);
          for (int i = 0; i < 2; i++) {
            fitChecks(route, plan.bogies().get(i));
            check(
                new Vector3d(seats.get(i))
                        .rotate(plan.rotation())
                        .add(plan.position())
                        .distance(plan.bogies().get(i).pivot())
                    < .12,
                "rigid carriage seats match separate curve bogies");
          }
        }
    var shortCurve = (RailPlacement.Route) (d -> Math.abs(d) <= 1.05 ? circle(4).at(d) : null);
    fitChecks(shortCurve, RailPlacement.bogie(shortCurve, 0, BogieGeometry.RIDE_HEIGHT, 1));
    var sidewaysSeats = List.of(new Vector3d(-4, .25, 0), new Vector3d(4, .25, 0));
    var sidewaysCar =
        RailPlacement.carriage(
            circle(16), sidewaysSeats, 0, new Vector3d(0, 0, 1), BogieGeometry.RIDE_HEIGHT, 1);
    check(
        sidewaysCar.bogies().get(0).pivot().distance(sidewaysCar.bogies().get(1).pivot()) > 7.8,
        "mount layout controls spacing even after bogie turns sideways relative to carrier");
    var seats = List.of(new Vector3d(0, .25, -4), new Vector3d(0, .25, 0), new Vector3d(0, .25, 4));
    var plan =
        RailPlacement.carriage(
            straight, seats, 1, new Vector3d(0, 0, 1), BogieGeometry.RIDE_HEIGHT, 1);
    check(plan.bogies().size() == 3, "straight route accepts three supporting bogies");
    try {
      RailPlacement.carriage(
          straight(3), seats, 1, new Vector3d(0, 0, 1), BogieGeometry.RIDE_HEIGHT, 1);
      throw new AssertionError("short route must fail");
    } catch (IllegalArgumentException expected) {
      checks++;
    }
    check(
        seats.get(0).equals(new Vector3d(0, .25, -4)),
        "failed planning does not mutate mounting offsets");
    var a = new Vector3d(-2, 0, 0);
    var b = new Vector3d(2, 0, 0);
    var hit = RailPlacement.raySegment(new Vector3d(0, 2, 0), new Vector3d(0, -2, 0), a, b);
    check(
        hit.distanceSquared() < 1e-12 && Math.abs(hit.segmentFraction() - .5) < 1e-12,
        "vertical aim hits rail independent of player yaw");
    hit = RailPlacement.raySegment(new Vector3d(0, 0, -2), new Vector3d(0, 0, 2), a, b);
    check(hit.distanceSquared() < 1e-12, "perpendicular aim hits rail");
    hit =
        RailPlacement.raySegment(
            new Vector3d(0, 0, 0),
            new Vector3d(0, 0, 1),
            new Vector3d(0, 0, 3),
            new Vector3d(0, 0, 4));
    check(
        hit.distanceSquared() >= 4 && hit.rayFraction() == 1,
        "finite ray cannot reach distant track");
    var grade =
        (RailPlacement.Route)
            (d ->
                new RailPlacement.Point(
                    new Vector3d(0, d * .2, d * Math.sqrt(.96)),
                    new Vector3d(0, .2, Math.sqrt(.96)),
                    new Vector3d(0, Math.sqrt(.96), -.2)));
    fitChecks(grade, RailPlacement.bogie(grade, 0, BogieGeometry.RIDE_HEIGHT, 1));
    var gradeCar =
        RailPlacement.carriage(
            grade, seats, 1, new Vector3d(0, 0, 1), BogieGeometry.RIDE_HEIGHT, 1);
    check(gradeCar.rotation().isFinite(), "sloped carriage has finite orientation");
    var reverse =
        (RailPlacement.Route)
            (d -> {
              var p = circle(16).at(-d);
              return new RailPlacement.Point(p.position(), p.tangent().negate(), p.up());
            });
    fitChecks(reverse, RailPlacement.bogie(reverse, 0, BogieGeometry.RIDE_HEIGHT, 1));
    System.out.println("RailPlacementCheck: " + checks + " assertions passed");
  }
}
