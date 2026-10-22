package com.guidedrailways;

import java.util.ArrayDeque;

/** Reverse every registered construction edit on failure, including partial failing edits. */
final class ConstructionTransaction implements AutoCloseable {
  private final ArrayDeque<Runnable> undo = new ArrayDeque<>();

  void undo(Runnable action) {
    undo.push(action);
  }

  void commit() {
    undo.clear();
  }

  @Override
  public void close() {
    RuntimeException failure = null;
    while (!undo.isEmpty()) {
      try {
        undo.pop().run();
      } catch (RuntimeException error) {
        if (failure == null) failure = error;
        else failure.addSuppressed(error);
      }
    }
    if (failure != null) throw failure;
  }
}
