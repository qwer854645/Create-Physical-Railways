package com.guidedrailways;

import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class HardwareRenderData {
  public static final ModelProperty<Boolean> DYNAMIC = new ModelProperty<>();
  public static volatile boolean ready;

  private HardwareRenderData() {}
}
