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
    return console(bound, scheduled, status, sneaking, direction, notch, name, ConsoleReadout.EMPTY);
  }

  static List<Component> console(boolean bound, boolean scheduled, ConsoleStatus status, boolean sneaking,
      int direction, int notch, String name, ConsoleReadout readout) {
    var lines = new ArrayList<Component>();
    lines.add(Component.translatable("message.guidedrailways.driver_console." + (bound ? "bound" : "unbound")));
    if (bound) {
      lines.add(Component.translatable("message.guidedrailways.driver_console." + (scheduled ? "scheduled" : "manual")));
      boolean scheduleState = scheduled && readout.state() >= 0
          && (status == ConsoleStatus.READY || status == ConsoleStatus.ARRIVED || status == ConsoleStatus.WAIT_TIMER);
      lines.add(Component.translatable(scheduleState ? "gui.guidedrailways.auto.state." + readout.state()
          : "guidedrailways.console." + status.name().toLowerCase(java.util.Locale.ROOT)));
      if (sneaking) {
        if (!name.isBlank())
          lines.add(Component.translatable("message.guidedrailways.driver_console.name", UserText.userInput(name)));
        var directionText = Component.translatable("message.guidedrailways.driver_console.direction."
            + (direction > 0 ? "forward" : direction < 0 ? "reverse" : "neutral"));
        var gear = notch == 0 ? Component.translatable("message.guidedrailways.driver_console.coast")
            : Component.translatable("message.guidedrailways.driver_console."
                + (notch > 0 ? "power_notch" : "brake_notch"), Math.abs(notch));
        lines.add(Component.translatable("message.guidedrailways.driver_console.controls", directionText, gear));
        if (readout.speed() >= 0)
          lines.add(Component.translatable("message.guidedrailways.driver_console.actual_speed", decimal(readout.speed())));
        if (scheduled && !readout.target().isBlank())
          lines.add(Component.translatable("gui.guidedrailways.auto.target", UserText.userInput(readout.target())));
        if (scheduled && readout.total() > 0)
          lines.add(Component.translatable("message.guidedrailways.driver_console.journey",
              readout.entry(), readout.total(), readout.remaining() < 0 ? "—" : decimal(readout.remaining())));
        lines.add(Component.translatable("message.guidedrailways.driver_console.use"));
        lines.add(Component.translatable("message.guidedrailways.driver_console.settings"));
      }
    }
    return lines;
  }

  private static String decimal(int tenths) {
    return String.format(java.util.Locale.ROOT, "%.1f", tenths / 10.0);
  }
}
