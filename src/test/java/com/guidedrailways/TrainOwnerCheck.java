package com.guidedrailways;

import java.util.UUID;

public final class TrainOwnerCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(!TrainOwner.UNKNOWN.known(), "no recorded placement means unknown");
    check(TrainOwner.UNKNOWN.id() == null, "unknown owner is not a fabricated UUID");
    check(
        new TrainOwner(null, "Driver").equals(TrainOwner.UNKNOWN),
        "operator name alone cannot create ownership");
    check(TrainOwner.UNKNOWN.label().isEmpty(), "unknown label is localized by UI");
    var id = UUID.randomUUID();
    var owner = new TrainOwner(id, "Player");
    check(owner.known() && owner.id().equals(id), "placement UUID preserved");
    check(owner.label().equals("Player"), "saved profile label available offline");
    check(
        new TrainOwner(id, "").label().equals(id.toString()),
        "UUID identifies recorded owner without a cached name");
    check(
        new TrainOwner(id, null).label().equals(id.toString()),
        "missing name does not erase placement UUID");
    check(
        new TrainOwner(id, " Player\nName ").name().equals("Player Name"),
        "label is bounded plain text");
    check(
        new TrainOwner(id, "x".repeat(128)).name().length() == TrainName.MAX_LENGTH,
        "owner label fits protocol");
    check(new TrainOwner(id, "Player").equals(owner), "placement metadata stable across reload");
    System.out.println(
        "TrainOwnerCheck: " + checks + " provenance and unknown-state checks passed");
  }
}
