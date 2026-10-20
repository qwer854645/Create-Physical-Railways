package com.guidedrailways;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Player-facing messages stay translatable; technical exceptions remain in the log. */
final class UserText {
  static Component error(Throwable error) {
    String key = error.getMessage();
    if (key != null && key.startsWith("message.guidedrailways."))
      return Component.translatable(key);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .warn("[Interaction] operation failed", error);
    return Component.translatable("message.guidedrailways.operation_failed");
  }

  static List<Component> lines(Component component) {
    return Arrays.stream(component.getString().split("\n"))
        .map(line -> (Component) Component.literal(line))
        .toList();
  }

  static void tooltip(List<Component> tooltip, String key) {
    for (var line : lines(Component.translatable(key)))
      tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
  }
}
