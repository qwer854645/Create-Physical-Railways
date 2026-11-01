package com.guidedrailways;

import java.util.*;

public final class ScheduleForecastCheck {
  private static int checks;
  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
  private static ScheduleForecast.Step destination(String text) {
    return new ScheduleForecast.Step(ScheduleForecast.Kind.DESTINATION, text,
        java.util.regex.Pattern.quote(text).replace("*", "\\E.*\\Q"));
  }
  public static void main(String[] args) {
    var steps = List.of(destination("A"), destination("B"),
        new ScheduleForecast.Step(ScheduleForecast.Kind.TITLE, "Express", ""),
        new ScheduleForecast.Step(ScheduleForecast.Kind.OTHER, "", ""), destination("North*"), destination("D"));
    var future = ScheduleForecast.future(steps, 0, false, "");
    check(future.size() == 3, "metadata instructions do not create station rows");
    check(future.get(0).title().equals("North"), "blank title uses next destination filter");
    check(future.get(1).title().equals("Express") && future.get(2).title().equals("Express"),
        "future title instruction affects subsequent destinations only");
    check(future.get(1).pattern().matcher("North Pier").matches(), "future wildcard matches native-style station names");
    check(future.get(0).literal() && !future.get(1).literal(), "exact names use indexed lookups; wildcards retain pattern checks");
    check(ScheduleForecast.future(steps, 5, false, "").isEmpty(), "non-cyclic final entry cannot wrap");
    var cyclic = ScheduleForecast.future(steps, 5, true, "Local");
    check(cyclic.size() == 3 && cyclic.get(0).filter().equals("A"), "cyclic itinerary wraps once");
    check(cyclic.stream().noneMatch(s -> s.filter().equals("D")), "current cyclic entry is not repeated");
    check(cyclic.get(0).title().equals("Local") && cyclic.get(2).title().equals("Express"),
        "cyclic title changes retain instruction order");
    check(ScheduleForecast.future(List.of(), 0, true, "").isEmpty(), "empty itinerary is safe");
    check(ScheduleForecast.future(steps, 6, true, "").isEmpty(), "completed schedule has no future rows");
    var many = new ArrayList<ScheduleForecast.Step>();
    for (int i = 0; i < 500; i++) many.add(destination("Stop " + i));
    check(ScheduleForecast.future(many, 0, true, "").size() == ScheduleForecast.MAX_STOPS,
        "very long schedules have bounded forecast work and output");
    var invalid = List.of(destination("A"), new ScheduleForecast.Step(ScheduleForecast.Kind.DESTINATION, "[", "["), destination("B"));
    check(ScheduleForecast.future(invalid, 0, false, "").isEmpty(), "invalid later filters cannot break native displays");
    System.out.println("ScheduleForecastCheck: " + checks + " itinerary, title, wildcard, cycle and bound checks passed");
  }
}
