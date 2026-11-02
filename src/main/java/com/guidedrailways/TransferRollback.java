package com.guidedrailways;

import java.util.List;
import java.util.function.*;

/** Recover independent owners without making another source while a destination still exists. */
final class TransferRollback {
  private TransferRollback() {}

  static <K> boolean restore(List<K> owners, Predicate<K> sourcePresent, Predicate<K> targetPresent,
      Consumer<K> removeTarget, Consumer<K> restoreSource, Consumer<RuntimeException> report) {
    boolean complete = true;
    for (var owner : owners) {
      try { if (targetPresent.test(owner)) removeTarget.accept(owner); }
      catch (RuntimeException failure) { report.accept(failure); complete = false; }
    }
    for (var owner : owners) {
      try {
        if (targetPresent.test(owner)) { complete = false; continue; }
        if (!sourcePresent.test(owner)) restoreSource.accept(owner);
        if (!sourcePresent.test(owner)) complete = false;
      } catch (RuntimeException failure) { report.accept(failure); complete = false; }
    }
    return complete;
  }
}
