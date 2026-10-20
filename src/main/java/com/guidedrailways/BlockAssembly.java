package com.guidedrailways;

import java.util.*;
import java.util.function.*;

/** A complete preflight before the first write; unchanged targets are validated too. */
final class BlockAssembly {
  record Target<K, S>(K key, S state) {}

  record Edit<K, S>(K key, S before, S after) {}

  private BlockAssembly() {}

  static <K, S> Optional<List<Edit<K, S>>> plan(
      Collection<Target<K, S>> targets, Function<K, S> read, Predicate<S> replaceable) {
    var edits = new ArrayList<Edit<K, S>>();
    var keys = new HashSet<K>();
    for (var target : targets) {
      if (!keys.add(target.key())) throw new IllegalArgumentException("Duplicate assembly target");
      var before = read.apply(target.key());
      if (!Objects.equals(before, target.state()) && !replaceable.test(before))
        return Optional.empty();
      edits.add(new Edit<>(target.key(), before, target.state()));
    }
    return Optional.of(List.copyOf(edits));
  }
}
