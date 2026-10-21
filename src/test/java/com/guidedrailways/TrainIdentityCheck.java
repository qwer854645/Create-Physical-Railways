package com.guidedrailways;

import java.util.*;

public final class TrainIdentityCheck {
  private static int checks;

  private static void check(boolean ok, String label) {
    checks++;
    if (!ok) throw new AssertionError(label);
  }

  private static UUID id(long value) {
    return new UUID(0, value);
  }

  public static void main(String[] args) {
    for (int order = 0; order < 2; order++) {
      var ledger = new TrainIdentityLedger();
      var a = Set.of(id(1), id(2));
      var b = Set.of(id(3), id(4));
      var all = Set.of(id(1), id(2), id(3), id(4));
      var first = ledger.reconcile(List.of(all), Set.copyOf(List.of(all))).get(all);
      check(first != null, "first identity");
      for (int i = 0; i < 1000; i++)
        check(
            first.equals(ledger.reconcile(List.of(all), Set.copyOf(List.of(all))).get(all)),
            "tick stable");
      var persisted = new TrainIdentityLedger();
      persisted.records.putAll(ledger.records);
      check(
          first.equals(persisted.reconcile(List.of(all), Set.copyOf(List.of(all))).get(all)),
          "reload stable");
      var before = Map.copyOf(ledger.records);
      check(
          ledger.reconcile(List.of(a), Set.copyOf(List.of(a))).isEmpty(),
          "anchor-only partial load is not a split");
      check(ledger.records.equals(before), "partial anchor load retains unloaded members");
      check(
          ledger.reconcile(List.of(b), Set.copyOf(List.of(b))).isEmpty(),
          "unloaded anchor is not a new identity");
      check(ledger.records.equals(before), "partial tail load retains identity");
      check(
          ledger.reconcile(List.of(), Set.copyOf(List.of())).isEmpty()
              && ledger.records.equals(before),
          "unload is not deletion");
      var split =
          ledger.reconcile(
              order == 0 ? List.of(a, b) : List.of(b, a),
              Set.copyOf(order == 0 ? List.of(a, b) : List.of(b, a)));
      check(first.equals(split.get(a)), "anchor split keeps identity independent of group order");
      check(
          split.get(b) != null && !first.equals(split.get(b)),
          "split tail receives distinct identity");
      check(ledger.records.size() == 2, "exactly two split records");
      var merged = ledger.reconcile(List.of(all), Set.copyOf(List.of(all))).get(all);
      check(ledger.records.size() == 1, "merge retires other identity");
      check(
          merged.equals(first) || merged.equals(split.get(b)), "merge keeps an existing identity");
      ledger.removed(id(1));
      check(
          merged.equals(
              ledger
                  .reconcile(
                      List.of(Set.of(id(2), id(3), id(4))),
                      Set.copyOf(List.of(Set.of(id(2), id(3), id(4)))))
                  .values()
                  .iterator()
                  .next()),
          "anchor deletion reanchors");
      ledger.removed(id(2));
      ledger.removed(id(3));
      ledger.removed(id(4));
      check(ledger.records.isEmpty(), "confirmed destruction removes identity");
    }
    for (int order = 0; order < 2; order++) {
      var ledger = new TrainIdentityLedger();
      var a = Set.of(id(10));
      var b = Set.of(id(11));
      var all = Set.of(id(10), id(11));
      check(
          ledger.reconcile(List.of(a, b), Set.of()).isEmpty() && ledger.records.isEmpty(),
          "loose bogies never allocate train identities");
      var controlled = ledger.reconcile(List.of(a, b), Set.of(a));
      var original = controlled.get(a);
      check(
          original != null && !controlled.containsKey(b) && ledger.records.size() == 1,
          "only the console group becomes a train");
      ledger.reconcile(List.of(a, b), Set.of());
      check(
          original.equals(ledger.reconcile(List.of(a, b), Set.of(a)).get(a)),
          "console replacement can retain the dormant identity");
      check(
          original.equals(ledger.reconcile(List.of(all), Set.of(all)).get(all)),
          "unpowered trailers join the one controlled train");
      var split = ledger.reconcile(order == 0 ? List.of(a, b) : List.of(b, a), Set.of(a));
      check(
          original.equals(split.get(a)) && !split.containsKey(b),
          "uncontrolled split tail does not block or create a second train");
      check(
          ledger.records.size() == 1 && ledger.records.get(original).members().equals(a),
          "detached trailer is removed from the original identity membership");
      ledger.reconcile(List.of(all), Set.of(all));
      split = ledger.reconcile(order == 0 ? List.of(a, b) : List.of(b, a), Set.of(b));
      check(
          split.get(b) != null && !original.equals(split.get(b)),
          "controlled split tail receives its own identity while old anchor remains dormant");
    }
    var random = new Random(9123);
    for (int count = 1; count <= 64; count++) {
      var ledger = new TrainIdentityLedger();
      var groups = new ArrayList<Set<UUID>>();
      for (int i = 0; i < count; i++) groups.add(Set.of(id(i + 1)));
      var expected = ledger.reconcile(groups, Set.copyOf(groups));
      for (int j = 0; j < 20; j++) {
        Collections.shuffle(groups, random);
        check(
            expected.equals(ledger.reconcile(groups, Set.copyOf(groups))),
            "independent groups remain stable when reordered");
        check(new HashSet<>(expected.values()).size() == count, "no duplicate live UUIDs");
      }
    }
    System.out.println("TrainIdentityCheck: " + checks + " lifecycle and ordering checks passed");
  }
}
