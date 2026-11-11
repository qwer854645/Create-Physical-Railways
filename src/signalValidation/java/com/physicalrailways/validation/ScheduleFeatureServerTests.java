package com.physicalrailways.validation;

import com.physicalrailways.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.display.GlobalTrainDisplayData;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;

/** Real registered conditions and native display aggregation; does not claim physical multi-stop travel. */
@GameTestHolder("physicalrailways_signal_validation")
@PrefixGameTestTemplate(false)
public final class ScheduleFeatureServerTests {
  static java.lang.reflect.Field member(Object object, String name) throws Exception {
    for (var type = object.getClass(); type != null; type = type.getSuperclass())
      try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
      catch (NoSuchFieldException ignored) {}
    throw new NoSuchFieldException(name);
  }
  static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
    var method = object.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(object, args);
  }
  static DestinationInstruction destination(String name) {
    var instruction = new DestinationInstruction();
    instruction.getData().putString("Text", name);
    return instruction;
  }
  static ScheduleEntry entry(ScheduleInstruction instruction) {
    return new ScheduleEntry(instruction, List.of());
  }

  @GameTest(template = "empty", templateNamespace = "physicalrailways_signal_validation", timeoutTicks = 2400)
  public static void nativeConditionsAndFutureDisplays(GameTestHelper helper) throws Exception {
    new Run(helper).start();
  }

  static final class Run {
    final GameTestHelper helper;
    final ServerLevel level;
    final TrackGraph graph = new TrackGraph();
    final BlockPos consolePos = new BlockPos(8192, 80, 256);
    final List<GlobalStation> stops = new ArrayList<>();
    DriverConsoleEntity console;
    Object controller;
    Train proxy;
    ScheduledDelay delay;
    final CompoundTag nativeClock = new CompoundTag();
    int ticks, loadTicks;
    boolean forced;

    Run(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }

    void start() throws Exception {
      var chunk = new net.minecraft.world.level.ChunkPos(consolePos);
      forced = !level.getForcedChunks().contains(chunk.toLong());
      level.getChunk(chunk.x, chunk.z);
      if (forced) level.setChunkForced(chunk.x, chunk.z, true);
      level.setBlock(consolePos, Content.DRIVER.get().defaultBlockState(), 3);
      console = (DriverConsoleEntity) level.getBlockEntity(consolePos);
      controller = member(console, "schedule").get(console);
      var a = JunctionServerTests.node(graph, level, 8192, 0);
      var b = JunctionServerTests.node(graph, level, 8192, 20);
      JunctionServerTests.connect(graph, a, b);
      for (String name : List.of("A", "B", "North 1", "North 2", "B")) {
        var stop = EdgePointType.STATION.create();
        stop.id = UUID.randomUUID(); stop.name = name;
        stop.blockEntityDimension = level.dimension(); stop.blockEntityPos = consolePos;
        stop.edgeLocation = Couple.create(a.getLocation(), b.getLocation()); stop.position = 5;
        graph.addPoint(EdgePointType.STATION, stop);
        stops.add(stop);
      }
      Create.RAILWAYS.trackNetworks.put(graph.id, graph);
      var constructor = PhysicalTrainProxy.class.getDeclaredConstructor(UUID.class);
      constructor.setAccessible(true);
      proxy = constructor.newInstance(UUID.randomUUID());
      proxy.graph = graph; proxy.derailed = false;
      Create.RAILWAYS.trains.put(proxy.id, proxy);
      var supported = controller.getClass().getDeclaredMethod("supportedCondition", ScheduleWaitCondition.class);
      supported.setAccessible(true);
      for (var factory : Schedule.CONDITION_TYPES)
        if (factory.getFirst().getNamespace().equals("create"))
          helper.assertTrue((boolean) supported.invoke(null, factory.getSecond().get()),
              "registered native condition accepted: " + factory.getFirst());
      var unsupported = controller.getClass().getDeclaredMethod("unsupported", Schedule.class);
      unsupported.setAccessible(true);
      for (var factory : Schedule.INSTRUCTION_TYPES)
        if (factory.getFirst().getNamespace().equals("create")) {
          var result = unsupported.invoke(null, new Schedule(List.of(entry(factory.getSecond().get())), false, 0));
          helper.assertTrue(result == null || result.equals("simulated"),
              "registered native instruction supported or explicitly requires existing docking: " + factory.getFirst());
        }
      delay = new ScheduledDelay();
      delay.getData().putInt("Value", 1);
      var schedule = new Schedule(List.of(new ScheduleEntry(destination("A"),
          List.of(List.of(new PlayerPassengerCondition()), List.of(delay)))), false, 0);
      member(controller, "program").set(controller, schedule);
      helper.runAfterDelay(1, this::awaitReady);
    }

    void awaitReady() {
      if (!level.isPositionEntityTicking(consolePos)) {
        helper.assertTrue(++loadTicks < 2000, "forced station chunk becomes entity ticking");
        helper.runAfterDelay(1, this::awaitReady);
        return;
      }
      tickDelay();
    }

    void tickDelay() {
      try {
        ticks++;
        boolean expected = delay.tickCompletion(level, null, nativeClock);
        boolean actual = (boolean) call(controller, "tickConditions", new Class<?>[] {ServerLevel.class}, level);
        helper.assertTrue(actual == expected, "later OR timer branch advances at native tick rate");
        if (!actual) {
          helper.assertTrue(ticks < 200, "timer branch cannot be starved by missing passenger data");
          helper.runAfterDelay(1, this::tickDelay);
          return;
        }
        helper.assertTrue(ticks == delay.totalWaitTicks() + 1, "native delay duration remains exact");
        helper.assertTrue(level.isPositionEntityTicking(consolePos), "forced station chunk is entity ticking");
        var current = stops.getFirst();
        call(proxy, "station", new Class<?>[] {UUID.class}, current.id);
        current.reserveFor(proxy);
        member(controller, "station").set(controller, current.id);
        var unloaded = new StationUnloadedCondition();
        boolean loaded = condition(unloaded);
        helper.assertTrue(!loaded && loaded == unloaded.tickCompletion(level, proxy, new CompoundTag()),
            "loaded entity-ticking station does not satisfy unload condition");
        var distant = new BlockPos(131072, 80, 131072);
        helper.assertTrue(!level.hasChunkAt(distant), "unload fixture chunk starts absent");
        current.blockEntityPos = distant;
        helper.assertTrue(condition(unloaded) && unloaded.tickCompletion(level, proxy, new CompoundTag()),
            "unloaded station matches native completion");
        helper.assertTrue(!level.hasChunkAt(distant), "unload query never forces a chunk load");
        member(controller, "station").set(controller, UUID.randomUUID());
        helper.assertTrue(!condition(unloaded), "missing station is not confused with unloaded station");
        member(controller, "station").set(controller, current.id);
        var dimension = current.blockEntityDimension;
        current.blockEntityDimension = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("physicalrailways", "missing_fixture"));
        helper.assertTrue(!condition(unloaded), "absent dimension cannot complete the unload condition");
        current.blockEntityDimension = dimension;
        current.blockEntityPos = consolePos;
        verifyDisplays();
        cleanup();
        org.slf4j.LoggerFactory.getLogger("physicalrailways.schedule-validation")
            .info("SCHEDULE_FEATURE_PASS nativeDelayTicks={} stationUnload=true futureDisplays=true", ticks);
        helper.succeed();
      } catch (Throwable error) {
        org.slf4j.LoggerFactory.getLogger("physicalrailways.schedule-validation").error("SCHEDULE_FEATURE_FAILED", error);
        cleanup(); helper.fail(error.toString());
      }
    }

    boolean condition(ScheduleWaitCondition condition) throws Exception {
      return (boolean) call(controller, "conditionComplete", new Class<?>[] {ServerLevel.class,
          ScheduleWaitCondition.class, CompoundTag.class, boolean.class}, level, condition, new CompoundTag(), true);
    }

    void verifyDisplays() throws Exception {
      call(proxy, "station", new Class<?>[] {UUID.class}, (Object) null);
      var title = new ChangeTitleInstruction(); title.getData().putString("Text", "Express");
      var schedule = new Schedule(List.of(entry(destination("A")), entry(destination("B")),
          entry(title), entry(destination("North*")), entry(destination("B"))), false, 0);
      var data = new CompoundTag();
      data.put("Program", schedule.write(level.registryAccess()));
      var navigation = new CompoundTag(); navigation.putUUID("Destination", stops.getFirst().id);
      navigation.putInt("ArrivalTicks", 120); data.put("Navigation", navigation);
      proxy.navigation.destination = stops.getFirst();
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      var predictions = proxy.runtime.submitPredictions();
      helper.assertTrue(predictions.size() == 4, "current plus future stations deduplicate identical names");
      helper.assertTrue(predictions.stream().anyMatch(p -> p.destination.equals("A") && p.ticks == 120),
          "current confirmed arrival retains physical estimate");
      helper.assertTrue(predictions.stream().filter(p -> !p.destination.equals("A")).allMatch(p -> p.ticks == -1),
          "unplanned future legs always use native unknown-time sentinel");
      GlobalTrainDisplayData.refresh();
      helper.assertTrue(GlobalTrainDisplayData.prepare("B", 20).stream().anyMatch(p -> p.train == proxy && p.ticks == -1),
          "native station summary aggregation exposes future train row");
      helper.assertTrue(predictions.stream().filter(p -> p.destination.startsWith("North"))
          .allMatch(p -> p.scheduleTitle.getString().equals("Express")), "future title instruction is reflected");
      for (var stop : stops) if (stop.name.equals("B")) stop.name = "Renamed";
      GlobalTrainDisplayData.refresh();
      helper.assertTrue(GlobalTrainDisplayData.prepare("B", 20).stream().noneMatch(p -> p.train == proxy),
          "renaming stations invalidates literal future destination matches");
      for (var stop : stops) if (stop.name.equals("Renamed")) stop.name = "B";
      graph.removePoint(EdgePointType.STATION, stops.get(3).id);
      helper.assertTrue(proxy.runtime.submitPredictions().stream().noneMatch(p -> p.destination.equals("North 2")),
          "station removal clears matching future row on the next display query");
      graph.addPoint(EdgePointType.STATION, stops.get(3));
      data.putBoolean("Paused", true);
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      GlobalTrainDisplayData.refresh();
      helper.assertTrue(GlobalTrainDisplayData.prepare("B", 20).stream().noneMatch(p -> p.train == proxy),
          "pausing removes all future rows from native aggregation");
      data.putBoolean("Paused", false); data.putBoolean("Completed", true);
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().isEmpty(), "completion removes future rows");
      data.putBoolean("Completed", false);
      data.putInt("Entry", 4);
      data.putString("Title", "Express");
      navigation.putUUID("Destination", stops.get(1).id);
      proxy.navigation.destination = stops.get(1);
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().size() == 1, "last non-cyclic stop cannot announce a wrapped trip");
      schedule.cyclic = true;
      data.put("Program", schedule.write(level.registryAccess()));
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().stream().anyMatch(p -> p.destination.equals("A") && p.ticks == -1),
          "cyclic itinerary publishes next-cycle destinations without guessing arrival time");
      call(proxy, "station", new Class<?>[] {UUID.class}, stops.get(1).id);
      stops.get(1).reserveFor(proxy);
      proxy.navigation.destination = null;
      data.putBoolean("Waiting", true);
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().stream().anyMatch(p -> p.destination.equals("B") && p.ticks == 0),
          "arrived prediction still requires real two-way station binding");
      stops.get(1).cancelReservation(proxy);
      helper.assertTrue(proxy.runtime.submitPredictions().isEmpty(), "lost station binding withdraws current and future predictions");

      var many = new Schedule(List.of(entry(destination("A")), entry(destination("Overflow*"))), false, 0);
      for (int i = 0; i < 40; i++) {
        var extra = EdgePointType.STATION.create();
        extra.id = UUID.randomUUID(); extra.name = "Overflow " + i;
        extra.blockEntityDimension = level.dimension(); extra.blockEntityPos = consolePos;
        extra.edgeLocation = stops.getFirst().edgeLocation; extra.position = 5;
        graph.addPoint(EdgePointType.STATION, extra);
      }
      call(proxy, "station", new Class<?>[] {UUID.class}, (Object) null);
      proxy.navigation.destination = stops.getFirst();
      data.putBoolean("Waiting", false); data.putInt("Entry", 0);
      navigation.putUUID("Destination", stops.getFirst().id);
      data.put("Program", many.write(level.registryAccess()));
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().size() == 32, "wildcard expansion has a strict per-train output cap");
      var invalid = new Schedule(List.of(entry(destination("A")), entry(destination("[")), entry(destination("B"))), false, 0);
      data.put("Program", invalid.write(level.registryAccess()));
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, data, level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().size() == 1, "invalid later glob cannot break the current train display");
      var unsupported = controller.getClass().getDeclaredMethod("unsupported", Schedule.class);
      unsupported.setAccessible(true);
      helper.assertTrue(unsupported.invoke(null, invalid) != null, "invalid destination globs are rejected on schedule installation");
      call(proxy.runtime, "project", new Class<?>[] {CompoundTag.class, HolderLookup.Provider.class}, new CompoundTag(), level.registryAccess());
      helper.assertTrue(proxy.runtime.submitPredictions().isEmpty(), "removing a schedule clears every forecast row");
    }

    void cleanup() {
      if (proxy != null) Create.RAILWAYS.trains.remove(proxy.id);
      Create.RAILWAYS.trackNetworks.remove(graph.id);
      level.removeBlock(consolePos, false);
      if (forced) {
        var chunk = new net.minecraft.world.level.ChunkPos(consolePos);
        level.setChunkForced(chunk.x, chunk.z, false);
      }
      GlobalTrainDisplayData.refresh();
    }
  }
}
