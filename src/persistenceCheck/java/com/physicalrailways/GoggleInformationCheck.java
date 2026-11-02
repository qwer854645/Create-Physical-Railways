package com.physicalrailways;

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
    for (var m : code("com/physicalrailways/" + owner).methods)
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
        List.of("message.physicalrailways.driver_console.unbound")), "unbound console has no false schedule claim");
    check(keys(GoggleText.console(true, false, ConsoleStatus.READY, false)).equals(List.of(
        "message.physicalrailways.driver_console.bound", "message.physicalrailways.driver_console.manual",
        "physicalrailways.console.ready")),
        "client synchronized bound state shows a manual console");
    for (var status : ConsoleStatus.values()) {
      var ordinary = GoggleText.console(true, true, status, false);
      var sneaking = GoggleText.console(true, true, status, true);
      check(ordinary.size() == 3 && sneaking.size() == 6, "compact summary; controls only when sneaking");
      check(keys(sneaking).get(1).endsWith(".scheduled"), "installed schedule recognized");
      check(keys(sneaking).get(2).equals("physicalrailways.console." + status.name().toLowerCase(java.util.Locale.ROOT)),
          "synchronized reason retained: " + status);
    }
    var named = GoggleText.console(true, false, ConsoleStatus.READY, true, -1, 3, "Local train");
    check(named.size() == 7 && keys(named).get(3).endsWith(".name"), "named details are limited to seven lines");
    check(GoggleText.console(true, false, ConsoleStatus.READY, false, -1, 3, "Local train").size() == 3,
        "name and controls never expand ordinary operation overlay");
    check(!calls("DriverConsoleEntity", "addToGoggleTooltip", "com/physicalrailways/DriverControls", "body"),
        "client goggles cannot query server-only body resolution");
    for (String owner : List.of("DriverConsoleEntity", "RailDockEntity", "mixin/RailwayCouplerMixin")) {
      String method = owner.startsWith("mixin/") ? "physicalrailways$goggles" : "addToGoggleTooltip";
      check(calls(owner, method, "com/physicalrailways/GoggleText", "add"), "native indentation used by " + owner);
    }
    check(calls("GoggleText", "add", "net/createmod/catnip/lang/LangBuilder", "forGoggles"),
        "goggles reserve icon width through the installed Create API");
    check(calls("RailDockInteractions", "click", "com/physicalrailways/RailDockEntity", "addInformation"),
        "chat uses server-safe information");
    check(!calls("RailDockEntity", "addInformation", "com/physicalrailways/GoggleText", "add"),
        "server chat never accesses the client font");
    var readout = ConsoleReadout.of("Central", 1, 2, 4, 123.45, -2.46);
    check(!readout.changedFrom(readout, ConsoleStatus.READY, ConsoleStatus.READY),
        "unchanged information does not trigger redundant synchronization");
    check(readout.changedFrom(readout, ConsoleStatus.READY, ConsoleStatus.SWITCH_HELD),
        "a stopped train synchronizes a new waiting reason even when every reading stays unchanged");
    check(readout.changedFrom(readout, ConsoleStatus.SWITCH_HELD, ConsoleStatus.READY),
        "clearing a waiting reason also triggers synchronization without movement");
    check(readout.changedFrom(ConsoleReadout.EMPTY, ConsoleStatus.READY, ConsoleStatus.READY),
        "numeric and journey changes still trigger synchronization");
    check(calls("DriverConsoleEntity", "refreshReadout", "com/physicalrailways/DriverConsoleEntity", "resolveStatus")
        && calls("DriverConsoleEntity", "refreshReadout", "com/physicalrailways/ConsoleReadout", "changedFrom"),
        "periodic synchronization includes current waiting reasons in its change decision");
    check(ConsoleReadout.read(readout.write()).equals(readout), "display snapshot survives its client payload");
    check(ConsoleReadout.read(new net.minecraft.nbt.CompoundTag()).equals(ConsoleReadout.EMPTY),
        "old payloads have no invented distance or speed");
    check(readout.speed() == 25 && readout.remaining() == 1235, "speed magnitude and distance use tenths");
    var summary = GoggleText.console(true, true, ConsoleStatus.READY, false, 1, 3, "Express", readout);
    check(summary.size() == 3 && keys(summary).get(2).equals("gui.physicalrailways.auto.state.1"),
        "a paused schedule never claims to be ready; ordinary overlay remains three lines");
    var expanded = GoggleText.console(true, true, ConsoleStatus.READY, true, 1, 3, "Express", readout);
    check(expanded.size() == 10, "all named journey details fit within ten sneaking lines");
    check(keys(expanded).contains("message.physicalrailways.driver_console.actual_speed")
        && keys(expanded).contains("gui.physicalrailways.auto.target")
        && keys(expanded).contains("message.physicalrailways.driver_console.journey"),
        "expanded journey includes actual speed, destination, progress and remaining distance");
    check(keys(GoggleText.console(true, true, ConsoleStatus.SWITCH_HELD, false, 1, 3, "", readout))
        .get(2).equals("physicalrailways.console.switch_held"), "junction refusal outranks general schedule state");
    var unknown = ConsoleReadout.of("", 2, 0, 4, Double.NaN, Double.NaN);
    check(unknown.remaining() == -1 && unknown.speed() == -1, "unknown values never masquerade as zero");
    var unknownLines = GoggleText.console(true, true, ConsoleStatus.READY, true, 1, 0, "", unknown);
    check(!keys(unknownLines).contains("message.physicalrailways.driver_console.actual_speed")
        && !keys(unknownLines).contains("gui.physicalrailways.auto.target"), "unavailable readings stay hidden");
    check(calls("DriverControls", "publish", "com/physicalrailways/DriverConsoleEntity", "refreshReadout"),
        "readout refresh runs for unattended schedules as well as occupied consoles");
    check(!calls("DriverConsoleEntity", "saveAdditional", "com/physicalrailways/ConsoleReadout", "write"),
        "transient display snapshot is never persisted as authoritative train state");
    System.out.println("GoggleInformationCheck: compact and expanded journey readouts, client payload, native indentation and server-chat separation passed");
  }
}
