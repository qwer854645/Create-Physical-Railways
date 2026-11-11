package com.physicalrailways;

/** Tick-clocked recovery; repeated physics substeps never advance the stable window. */
public final class RecoveryWindow {
  static final int RETRY_TICKS = 20, MAX_RETRY_TICKS = 200, STABLE_TICKS = 10;
  static final int QUIET_TICKS = 400, MAX_FAILURES = 5;
  private long last = Long.MIN_VALUE, retryAt, stableAt = Long.MIN_VALUE;
  private int failures, stable;
  private long lastFailure = Long.MIN_VALUE;
  private boolean blocked;

  private void clock(long now) {
    if (last != Long.MIN_VALUE && now < last && blocked) {
      retryAt = now + RETRY_TICKS;
      stable = 0;
      stableAt = Long.MIN_VALUE;
    }
    last = now;
  }

  public boolean blocked() {
    return blocked;
  }

  public boolean probe(long now) {
    clock(now);
    return !blocked || now >= retryAt;
  }

  public void unavailable(long now) {
    clock(now);
    blocked = true;
    stable = 0;
    stableAt = Long.MIN_VALUE;
    retryAt = now + RETRY_TICKS;
  }

  public void failed(long now) {
    clock(now);
    // Once a failure opens the cooldown, more substeps cannot extend or escalate it.
    if (blocked && now < retryAt) return;
    if (lastFailure != Long.MIN_VALUE && (now < lastFailure || now - lastFailure >= QUIET_TICKS))
      failures = 0;
    lastFailure = now;
    failures = Math.min(MAX_FAILURES, failures + 1);
    unavailable(now);
    retryAt = now + Math.min(MAX_RETRY_TICKS, (long) RETRY_TICKS << (failures - 1));
  }

  public boolean healthy(long now) {
    clock(now);
    if (!blocked) return true;
    if (now < retryAt) return false;
    if (stableAt == now) return false;
    stable = stableAt == now - 1 ? stable + 1 : 1;
    stableAt = now;
    if (stable < STABLE_TICKS) return false;
    blocked = false;
    return true;
  }
}
