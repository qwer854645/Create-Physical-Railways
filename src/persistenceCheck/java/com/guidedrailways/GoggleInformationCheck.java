package com.guidedrailways;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.objectweb.asm.tree.*;

public final class GoggleInformationCheck {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input = GoggleInformationCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node, 0);
    }
    return node;
  }

  private static boolean calls(String owner, String method, String target, String called) throws Exception {
    for (var m : code("com/guidedrailways/" + owner).methods)
      if (m.name.equals(method))
        for (var insn : m.instructions)
          if (insn instanceof MethodInsnNode c && c.owner.equals(target) && c.name.equals(called)) return true;
    return false;
  }

  private static List<String> keys(List<Component> lines) {
    return lines.stream().map(c -> ((TranslatableContents) c.getContents()).getKey()).toList();
  }

  public static void main(String[] args) throws Exception {
    check(keys(GoggleText.console(false, true, ConsoleStatus.NO_BODY, true)).equals(
        List.of("message.guidedrailways.driver_console.unbound")), "unbound console has no false schedule claim");
    check(keys(GoggleText.console(true, false, ConsoleStatus.READY, false)).equals(List.of(
        "message.guidedrailways.driver_console.bound", "message.guidedrailways.driver_console.manual",
        "guidedrailways.console.ready")),
        "client synchronized bound state shows a manual console");
    for (var status : ConsoleStatus.values()) {
      var ordinary = GoggleText.console(true, true, status, false);
      var sneaking = GoggleText.console(true, true, status, true);
      check(ordinary.size() == 3 && sneaking.size() == 6, "compact summary; controls only when sneaking");
      check(keys(sneaking).get(1).endsWith(".scheduled"), "installed schedule recognized");
      check(keys(sneaking).get(2).equals("guidedrailways.console." + status.name().toLowerCase(java.util.Locale.ROOT)),
          "synchronized reason retained: " + status);
    }
    var named = GoggleText.console(true, false, ConsoleStatus.READY, true, -1, 3, "Local train");
    check(named.size() == 7 && keys(named).get(3).endsWith(".name"), "named details are limited to seven lines");
    check(GoggleText.console(true, false, ConsoleStatus.READY, false, -1, 3, "Local train").size() == 3,
        "name and controls never expand ordinary operation overlay");
    check(!calls("DriverConsoleEntity", "addToGoggleTooltip", "com/guidedrailways/DriverControls", "body"),
        "client goggles cannot query server-only body resolution");
    for (String owner : List.of("DriverConsoleEntity", "RailDockEntity", "mixin/RailwayCouplerMixin")) {
      String method = owner.startsWith("mixin/") ? "guidedrailways$goggles" : "addToGoggleTooltip";
      check(calls(owner, method, "com/guidedrailways/GoggleText", "add"), "native indentation used by " + owner);
    }
    check(calls("GoggleText", "add", "net/createmod/catnip/lang/LangBuilder", "forGoggles"),
        "goggles reserve icon width through the installed Create API");
    check(calls("RailDockInteractions", "click", "com/guidedrailways/RailDockEntity", "addInformation"),
        "chat uses server-safe information");
    check(!calls("RailDockEntity", "addInformation", "com/guidedrailways/GoggleText", "add"),
        "server chat never accesses the client font");
    System.out.println("GoggleInformationCheck: bound/manual/scheduled/reason states, native indentation and server-chat separation passed");
  }
}
