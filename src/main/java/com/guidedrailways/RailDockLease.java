package com.guidedrailways;

import java.util.UUID;

final class RailDockLease {
  private UUID owner;
  private long until, cooldown;

  boolean canClaim(UUID console, long now) {
    return now >= cooldown && (owner == null || now >= until || owner.equals(console));
  }

  boolean claim(UUID console, long now) {
    if (!canClaim(console, now)) return false;
    owner = console;
    until = now + 40;
    return true;
  }

  boolean active(long now) {
    return owner != null && now < until;
  }

  boolean release(UUID console, long now) {
    if (owner == null || !owner.equals(console)) return false;
    owner = null;
    until = 0;
    cooldown = now + 20;
    return true;
  }

  void clear(long now) {
    owner = null;
    until = 0;
    cooldown = now + 20;
  }
}
