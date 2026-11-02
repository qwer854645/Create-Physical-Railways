package com.physicalrailways;

import java.util.*;
import java.util.regex.*;

/** Bounded read-only itinerary. Future legs do not execute instructions or invent travel times. */
final class ScheduleForecast {
  static final int MAX_STOPS = 32, MAX_ENTRIES = 128;
  enum Kind { DESTINATION, TITLE, OTHER }
  record Step(Kind kind, String text, String regex) {}
  record Stop(String filter, Pattern pattern, String title, boolean literal) {}

  private static boolean literal(String filter) {
    // Matches the installed Create Glob.isGlobMeta definition. Regex punctuation in a plain
    // station name is escaped by Create, so a literal name can use the name index directly.
    for (int i = 0; i < filter.length(); i++) if ("\\*?[{".indexOf(filter.charAt(i)) >= 0) return false;
    return true;
  }

  static String title(List<Step> steps, int entry, boolean cyclic, String explicit) {
    if (!explicit.isBlank()) return explicit;
    for (int offset = 1; offset < steps.size() && offset <= MAX_ENTRIES; offset++) {
      int next = entry + offset;
      if (!cyclic && next >= steps.size()) break;
      var step = steps.get(next % steps.size());
      if (step.kind == Kind.DESTINATION) return step.text.replace("*", "").trim();
    }
    return "";
  }

  static List<Stop> future(List<Step> steps, int entry, boolean cyclic, String currentTitle) {
    if (entry < 0 || entry >= steps.size()) return List.of();
    var out = new ArrayList<Stop>();
    String explicit = currentTitle;
    for (int offset = 1; offset < steps.size() && offset <= MAX_ENTRIES && out.size() < MAX_STOPS; offset++) {
      int next = entry + offset;
      if (!cyclic && next >= steps.size()) break;
      int index = next % steps.size();
      var step = steps.get(index);
      if (step.kind == Kind.TITLE) explicit = step.text;
      if (step.kind != Kind.DESTINATION) continue;
      try {
        out.add(new Stop(step.text, Pattern.compile(step.regex),
            title(steps, index, cyclic, explicit), literal(step.text)));
      } catch (PatternSyntaxException invalid) {
        // A malformed later instruction must not break every native display source.
        break;
      }
    }
    return List.copyOf(out);
  }

  private ScheduleForecast() {}
}
