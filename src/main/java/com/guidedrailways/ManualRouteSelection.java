package com.guidedrailways;

/** One pending junction choice. Versions prevent an older route snapshot consuming a newer click. */
final class ManualRouteSelection {
  record Choice(int intent, long revision) {}
  private Choice choice = new Choice(0, 0);
  int intent() { return choice.intent; }
  Choice snapshot() { return choice; }
  boolean select(int next) {
    if (next < -1 || next > 1) throw new IllegalArgumentException("Invalid branch intent");
    if (next == choice.intent) return false;
    choice = new Choice(next, choice.revision + 1); return true;
  }
  boolean consume(Choice expected, boolean newPassage) {
    if (!newPassage || expected == null || expected.intent == 0
        || !expected.equals(choice)) return false;
    return select(0);
  }
}
