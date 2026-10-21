package com.guidedrailways;

import java.util.*;
import net.minecraft.nbt.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Real NBT and upstream bytecode checks; does not instantiate a Minecraft train outside NeoForge.
 */
public final class NativeJourneyCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var stream = NativeJourneyCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node, 0);
    }
    return node;
  }

  private static MethodNode method(ClassNode node, String name) {
    return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
  }

  private static List<MethodInsnNode> calls(MethodNode node) {
    var calls = new ArrayList<MethodInsnNode>();
    for (var i : node.instructions) if (i instanceof MethodInsnNode call) calls.add(call);
    return calls;
  }

  private static void noExecution(String name, List<String> methods) throws Exception {
    var node = code("com/guidedrailways/" + name);
    for (var method : methods) {
      var code = method(node, method);
      check(calls(code).isEmpty(), name + "." + method + " never calls upstream execution");
      for (var i : code.instructions) {
        check(
            !(i instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD,
            name + "." + method + " does not change mirrored state");
      }
    }
  }

  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    check(
        NativeTrainJourney.continuous(new int[0], new int[0]),
        "destination on occupied edge needs no future edge");
    check(
        NativeTrainJourney.continuous(new int[] {1, 2, 2, 3, 3, 4}, new int[] {1}),
        "future route with one junction");
    check(
        NativeTrainJourney.continuous(new int[] {4, 3, 3, 2, 2, 1}, new int[] {0, 2}),
        "reverse route retains directed node order");
    check(
        NativeTrainJourney.continuous(new int[] {1, 2, 2, 1}, new int[] {0}),
        "loop may return over an edge");
    check(
        !NativeTrainJourney.continuous(new int[] {1}, new int[0]), "truncated node pair rejected");
    check(!NativeTrainJourney.continuous(new int[] {1, 1}, new int[0]), "self edge rejected");
    check(
        !NativeTrainJourney.continuous(new int[] {1, 2, 3, 4}, new int[0]),
        "disconnected route rejected in full");
    check(
        !NativeTrainJourney.continuous(new int[] {1, 2}, new int[] {1}),
        "out-of-route junction rejected");
    check(
        !NativeTrainJourney.continuous(new int[] {1, 2}, new int[] {-1}),
        "negative junction rejected");
    check(
        !NativeTrainJourney.continuous(new int[] {1, 2, 2, 3}, new int[] {1, 0}),
        "reordered junctions rejected");
    check(
        !NativeTrainJourney.continuous(new int[] {1, 2}, new int[] {0, 0}),
        "duplicated junction rejected");
    for (int leg = 0; leg < 3; leg++) {
      int[] path = Arrays.copyOfRange(new int[] {1, 2, 2, 3, 3, 4}, leg * 2, 6);
      check(
          NativeTrainJourney.continuous(path, new int[] {0}),
          "route progress trims traversed edges");
    }
    var snapshot = new CompoundTag();
    var journey = new CompoundTag();
    var nav = new CompoundTag();
    var id = UUID.randomUUID();
    nav.putUUID("Destination", id);
    nav.putDouble("Remaining", 17.25);
    nav.putInt("ArrivalTicks", 120);
    nav.putBoolean("BehindTrain", true);
    nav.putIntArray("Path", new int[] {5, 6, 6, 7});
    nav.putIntArray("Branches", new int[] {1});
    journey.put("Navigation", nav);
    journey.putInt("Entry", 2);
    journey.putIntArray("Progress", new int[] {1, 0});
    var contexts = new ListTag();
    var context = new CompoundTag();
    context.putInt("Time", 90);
    contexts.add(context);
    journey.put("Contexts", contexts);
    snapshot.put("Journey", journey);
    var bytes = new java.io.ByteArrayOutputStream();
    NbtIo.write(snapshot, new java.io.DataOutputStream(bytes));
    var restored =
        NbtIo.read(
            new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
    check(
        snapshot.equals(restored),
        "navigation and condition contexts survive packet NBT round trip");
    check(
        id.equals(restored.getCompound("Journey").getCompound("Navigation").getUUID("Destination")),
        "station identity preserved");
    var isolated = restored.copy();
    isolated
        .getCompound("Journey")
        .getList("Contexts", Tag.TAG_COMPOUND)
        .getCompound(0)
        .putInt("Time", 0);
    check(context.getInt("Time") == 90, "query context does not mutate executing condition");

    var nativeNav = code("com/simibubi/create/content/trains/entity/Navigation");
    check(
        nativeNav.fields.stream()
            .anyMatch(f -> f.name.equals("currentPath") && f.desc.equals("Ljava/util/List;")),
        "installed native path accessor target");
    for (var field :
        List.of(
            "destination", "distanceToDestination", "distanceStartedAt", "destinationBehindTrain"))
      check(
          nativeNav.fields.stream()
              .anyMatch(f -> f.name.equals(field) && (f.access & Opcodes.ACC_PUBLIC) != 0),
          "native navigation query: " + field);
    var runtime = code("com/simibubi/create/content/trains/schedule/ScheduleRuntime");
    for (var field :
        List.of(
            "schedule",
            "paused",
            "completed",
            "currentEntry",
            "state",
            "conditionProgress",
            "conditionContext"))
      check(
          runtime.fields.stream()
              .anyMatch(f -> f.name.equals(field) && (f.access & Opcodes.ACC_PUBLIC) != 0),
          "native runtime query: " + field);
    noExecution(
        "PhysicalNavigation", List.of("tick", "cancelNavigation", "startNavigation", "read"));
    noExecution(
        "PhysicalScheduleRuntime",
        List.of(
            "tick",
            "tickConditions",
            "startCooldown",
            "destinationReached",
            "transitInterrupted",
            "startCurrentInstruction",
            "setSchedule",
            "discardSchedule",
            "read",
            "setSchedulePresentClientside",
            "returnSchedule"));
    var capture = method(code("com/guidedrailways/NativeTrainJourney"), "capture");
    check(
        calls(capture).stream()
            .noneMatch(
                c ->
                    Set.of("find", "startNavigation", "reserveFor", "advance", "destinationReached")
                        .contains(c.name)),
        "snapshot publication does not plan or execute a second journey");
    var proxy = code("com/guidedrailways/PhysicalTrainProxy");
    check(
        calls(method(proxy, "apply")).stream().filter(c -> c.name.equals("project")).count() == 2,
        "both native views refreshed by the same packet");
    check(
        calls(method(proxy, "clearJourney")).stream().filter(c -> c.name.equals("project")).count()
            == 2,
        "removal clears both views");
    var predictions =
        method(code("com/guidedrailways/PhysicalScheduleRuntime"), "submitPredictions");
    check(
        calls(predictions).stream().anyMatch(c -> c.name.equals("getCurrentStation"))
            && calls(predictions).stream().anyMatch(c -> c.name.equals("getPresentTrain")),
        "arrived prediction requires both sides of actual station association");
    check(
        calls(predictions).stream()
            .noneMatch(
                c ->
                    Set.of(
                            "findPathTo",
                            "startNavigation",
                            "reserveFor",
                            "advance",
                            "destinationReached")
                        .contains(c.name)),
        "display queries do not execute navigation or advance schedules");
    for (var instruction : predictions.instructions)
      check(
          !(instruction instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD,
          "display refresh does not mutate physical schedule state");
    var display = code("com/simibubi/create/content/trains/display/GlobalTrainDisplayData");
    check(
        calls(method(display, "refresh")).stream()
            .anyMatch(c -> c.name.equals("submitPredictions")),
        "installed native display consumes runtime predictions");
    var summary =
        code("com/simibubi/create/content/redstone/displayLink/source/StationSummaryDisplaySource");
    check(
        summary.methods.stream()
            .flatMap(m -> calls(m).stream())
            .anyMatch(c -> c.name.equals("prepare")),
        "installed station summary reads native train display data");
    var unknown =
        new com.simibubi.create.content.trains.display.GlobalTrainDisplayData
            .TrainDeparturePrediction(
            null, -1, net.minecraft.network.chat.Component.literal("Route"), "Station");
    var known =
        new com.simibubi.create.content.trains.display.GlobalTrainDisplayData
            .TrainDeparturePrediction(
            null, 400, net.minecraft.network.chat.Component.literal("Route"), "Station");
    check(unknown.compareTo(known) > 0, "native unknown-time sentinel sorts behind known arrivals");
    check(
        unknown.destination.equals("Station") && unknown.scheduleTitle.getString().equals("Route"),
        "native prediction carries actual station and journey title");
    System.out.println(
        "NativeJourneyCheck: " + checks + " route validation, NBT and isolation checks passed");
  }
}
