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
      var first = ledger.reconcile(List.of(all)).get(all);
      check(first != null, "first identity");
      for (int i = 0; i < 1000; i++)
        check(first.equals(ledger.reconcile(List.of(all)).get(all)), "tick stable");
      var persisted = new TrainIdentityLedger();
      persisted.records.putAll(ledger.records);
      check(first.equals(persisted.reconcile(List.of(all)).get(all)), "reload stable");
      var before = Map.copyOf(ledger.records);
      check(ledger.reconcile(List.of(a)).isEmpty(), "anchor-only partial load is not a split");
      check(ledger.records.equals(before), "partial anchor load retains unloaded members");
      check(ledger.reconcile(List.of(b)).isEmpty(), "unloaded anchor is not a new identity");
      check(ledger.records.equals(before), "partial tail load retains identity");
      check(
          ledger.reconcile(List.of()).isEmpty() && ledger.records.equals(before),
          "unload is not deletion");
      var split = ledger.reconcile(order == 0 ? List.of(a, b) : List.of(b, a));
      check(first.equals(split.get(a)), "anchor split keeps identity independent of group order");
      check(
          split.get(b) != null && !first.equals(split.get(b)),
          "split tail receives distinct identity");
      check(ledger.records.size() == 2, "exactly two split records");
      var merged = ledger.reconcile(List.of(all)).get(all);
      check(ledger.records.size() == 1, "merge retires other identity");
      check(
          merged.equals(first) || merged.equals(split.get(b)), "merge keeps an existing identity");
      ledger.removed(id(1));
      check(
          merged.equals(
              ledger.reconcile(List.of(Set.of(id(2), id(3), id(4)))).values().iterator().next()),
          "anchor deletion reanchors");
      ledger.removed(id(2));
      ledger.removed(id(3));
      ledger.removed(id(4));
      check(ledger.records.isEmpty(), "confirmed destruction removes identity");
    }
    var random = new Random(9123);
    for (int count = 1; count <= 64; count++) {
      var ledger = new TrainIdentityLedger();
      var groups = new ArrayList<Set<UUID>>();
      for (int i = 0; i < count; i++) groups.add(Set.of(id(i + 1)));
      var expected = ledger.reconcile(groups);
      for (int j = 0; j < 20; j++) {
        Collections.shuffle(groups, random);
        check(
            expected.equals(ledger.reconcile(groups)),
            "independent groups remain stable when reordered");
        check(new HashSet<>(expected.values()).size() == count, "no duplicate live UUIDs");
      }
    }
    System.out.println("TrainIdentityCheck: " + checks + " lifecycle and ordering checks passed");
  }
}
