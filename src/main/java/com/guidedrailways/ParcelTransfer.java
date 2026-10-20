package com.guidedrailways;

import java.util.*;
import java.util.function.Predicate;

/** Whole parcels only, into empty slots; failed writes restore both original slots. */
final class ParcelTransfer {
  interface Slots<T> {
    int size();

    T get(int slot); // null means empty

    boolean canTake(int slot, T parcel);

    boolean accepts(int slot, T parcel);

    void set(int slot, T parcel);
  }

  static <T> int move(
      Slots<T> source, List<? extends Slots<T>> targets, Predicate<T> eligible, int budget) {
    int moved = 0;
    for (int s = 0; s < source.size() && moved < budget; s++) {
      T parcel = source.get(s);
      if (parcel == null || !eligible.test(parcel) || !source.canTake(s, parcel)) continue;
      boolean done = false;
      for (var target : targets) {
        if (target == source) continue;
        for (int t = 0; t < target.size(); t++) {
          if (target.get(t) != null || !target.accepts(t, parcel)) continue;
          // Both endpoints are local modifiable inventories, on the server thread.
          try {
            target.set(t, parcel);
            source.set(s, null);
          } catch (RuntimeException failure) {
            try {
              target.set(t, null);
            } catch (RuntimeException rollback) {
              failure.addSuppressed(rollback);
            }
            try {
              source.set(s, parcel);
            } catch (RuntimeException rollback) {
              failure.addSuppressed(rollback);
            }
            throw failure;
          }
          moved++;
          done = true;
          break;
        }
        if (done) break;
      }
    }
    return moved;
  }
}
