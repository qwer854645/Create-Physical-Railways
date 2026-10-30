package com.guidedrailways;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Native goggles indentation reserves the overlay icon's space at the current font width. */
public final class GoggleText {
  private GoggleText() {}

  public static void add(List<Component> tooltip, Component line) {
    com.simibubi.create.foundation.utility.CreateLang.builder().add(line).forGoggles(tooltip);
  }

  static List<Component> console(boolean bound, boolean scheduled, ConsoleStatus status, boolean sneaking) {
    var lines = new ArrayList<Component>();
    lines.add(Component.translatable("message.guidedrailways.driver_console." + (bound ? "bound" : "unbound")));
    if (bound) {
      lines.add(Component.translatable("message.guidedrailways.driver_console." + (scheduled ? "scheduled" : "manual")));
      if (sneaking)
        lines.add(Component.translatable("guidedrailways.console." + status.name().toLowerCase(java.util.Locale.ROOT)));
    }
    return lines;
  }
}
