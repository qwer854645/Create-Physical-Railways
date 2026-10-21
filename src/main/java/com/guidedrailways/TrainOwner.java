package com.guidedrailways;

import java.util.UUID;

/** Placement provenance, not driving permission. Missing history stays unknown. */
public record TrainOwner(UUID id, String name) {
  public static final TrainOwner UNKNOWN = new TrainOwner(null, "");

  public TrainOwner {
    name = id == null ? "" : TrainName.normalize(name);
  }

  public boolean known() {
    return id != null;
  }

  public String label() {
    return !known() ? "" : name.isEmpty() ? id.toString() : name;
  }
}
