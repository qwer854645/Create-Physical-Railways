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
    return console(bound, scheduled, status, sneaking, 1, -4, "");
  }

  static List<Component> console(boolean bound, boolean scheduled, ConsoleStatus status, boolean sneaking,
      int direction, int notch, String name) {
    var lines = new ArrayList<Component>();
    lines.add(Component.translatable("message.guidedrailways.driver_console." + (bound ? "bound" : "unbound")));
    if (bound) {
      lines.add(Component.translatable("message.guidedrailways.driver_console." + (scheduled ? "scheduled" : "manual")));
      lines.add(Component.translatable("guidedrailways.console." + status.name().toLowerCase(java.util.Locale.ROOT)));
      if (sneaking) {
        if (!name.isBlank())
          lines.add(Component.translatable("message.guidedrailways.driver_console.name", UserText.userInput(name)));
        var directionText = Component.translatable("message.guidedrailways.driver_console.direction."
            + (direction > 0 ? "forward" : direction < 0 ? "reverse" : "neutral"));
        var gear = notch == 0 ? Component.translatable("message.guidedrailways.driver_console.coast")
            : Component.translatable("message.guidedrailways.driver_console."
                + (notch > 0 ? "power_notch" : "brake_notch"), Math.abs(notch));
        lines.add(Component.translatable("message.guidedrailways.driver_console.controls", directionText, gear));
        lines.add(Component.translatable("message.guidedrailways.driver_console.use"));
        lines.add(Component.translatable("message.guidedrailways.driver_console.settings"));
      }
    }
    return lines;
  }
}
