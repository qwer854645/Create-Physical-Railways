package com.guidedrailways;

import com.simibubi.create.content.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Read-only projection. The only executable schedule and physical item stay in the controls. */
final class PhysicalScheduleRuntime extends ScheduleRuntime {
  private CompoundTag program;
  private UUID predictionStation;
  private int arrivalTicks = -1;
  private List<ScheduleForecast.Step> forecastSteps = List.of();
  private List<ScheduleForecast.Stop> futureStops = List.of();
  private int forecastEntry = -1;
  private String forecastTitle = "";

  PhysicalScheduleRuntime(PhysicalTrainProxy train) {
    super(train);
  }

  void project(CompoundTag tag, HolderLookup.Provider registries) {
    var next = tag.contains("Program", Tag.TAG_COMPOUND) ? tag.getCompound("Program") : null;
    if (!Objects.equals(program, next)) {
      program = next == null ? null : next.copy();
      schedule = null;
      if (next != null) {
        try {
          schedule = Schedule.fromTag(registries, next.copy());
        } catch (RuntimeException error) {
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
              .warn(
                  "[NativeTrainJourney] cannot decode schedule query for train={}",
                  train.id,
                  error);
        }
      }
      forecastSteps = schedule == null ? List.of() : schedule.entries.stream().map(entry -> {
        var instruction = entry.instruction;
        if (instruction instanceof DestinationInstruction destination) {
          try {
            return new ScheduleForecast.Step(ScheduleForecast.Kind.DESTINATION,
                destination.getFilter(), net.createmod.catnip.data.Glob.toRegexPattern(destination.getFilter()));
          } catch (java.util.regex.PatternSyntaxException invalid) {
            // Old saves and externally supplied query data may contain an invalid later glob.
            // Stop forecasting at that entry rather than failing identity/display updates.
            return new ScheduleForecast.Step(ScheduleForecast.Kind.DESTINATION, destination.getFilter(), "[");
          }
        }
        if (instruction instanceof ChangeTitleInstruction title)
          return new ScheduleForecast.Step(ScheduleForecast.Kind.TITLE, title.getScheduleTitle(), "");
        return new ScheduleForecast.Step(ScheduleForecast.Kind.OTHER, "", "");
      }).toList();
      forecastEntry = -1;
    }
    paused = schedule == null || tag.getBoolean("Paused");
    completed = schedule != null && tag.getBoolean("Completed");
    isAutoSchedule = false;
    currentEntry =
        schedule == null ? 0 : Math.clamp(tag.getInt("Entry"), 0, schedule.entries.size());
    state =
        tag.getBoolean("Waiting") && schedule != null && currentEntry < schedule.entries.size()
            ? State.POST_TRANSIT
            : train.navigation.isActive() ? State.IN_TRANSIT : State.PRE_TRANSIT;
    currentTitle = tag.getString("Title");
    if (forecastEntry != currentEntry || !forecastTitle.equals(currentTitle)) {
      futureStops = schedule == null ? List.of()
          : ScheduleForecast.future(forecastSteps, currentEntry, schedule.cyclic, currentTitle);
      forecastEntry = currentEntry;
      forecastTitle = currentTitle;
    }
    conditionProgress.clear();
    conditionContext.clear();
    if (state == State.POST_TRANSIT) {
      var progress = tag.getIntArray("Progress");
      var contexts = tag.getList("Contexts", Tag.TAG_COMPOUND);
      var branches = schedule.entries.get(currentEntry).conditions;
      for (int i = 0; i < branches.size(); i++) {
        conditionProgress.add(
            i < progress.length ? Math.clamp(progress[i], 0, branches.get(i).size()) : 0);
        conditionContext.add(
            i < contexts.size() ? contexts.getCompound(i).copy() : new CompoundTag());
      }
    }
    var navigation = tag.getCompound("Navigation");
    predictionStation =
        navigation.hasUUID("Destination") ? navigation.getUUID("Destination") : null;
    arrivalTicks =
        navigation.contains("ArrivalTicks", Tag.TAG_INT)
            ? Math.max(-1, navigation.getInt("ArrivalTicks"))
            : -1;
    // Future legs have no confirmed route yet; do not invent durations for them.
    predictionTicks.clear();
    if (schedule != null) for (var entry : schedule.entries) predictionTicks.add(-1);
    ticksInTransit = 0;
    displayLinkUpdateRequested = false;
  }

  @Override
  public void tick(Level level) {}

  @Override
  public void tickConditions(Level level) {}

  @Override
  public void startCooldown() {}

  @Override
  public void destinationReached() {}

  @Override
  public void transitInterrupted() {}

  @Override
  public DiscoveredPath startCurrentInstruction(Level level) {
    return null;
  }

  @Override
  public void setSchedule(Schedule schedule, boolean auto) {}

  @Override
  public void discardSchedule() {}

  @Override
  public void read(HolderLookup.Provider registries, CompoundTag tag) {}

  @Override
  public void setSchedulePresentClientside(boolean present) {}

  @Override
  public ItemStack returnSchedule(HolderLookup.Provider registries) {
    return ItemStack.EMPTY;
  }

  @Override
  public Collection<TrainDeparturePrediction> submitPredictions() {
    if (schedule == null
        || paused
        || completed
        || currentEntry >= schedule.entries.size()
        || train.derailed
        || train.graph == null
        || predictionStation == null) return List.of();
    var station = train.graph.getPoint(EdgePointType.STATION, predictionStation);
    if (station == null || station.assembling) return List.of();
    int ticks;
    if (state == State.POST_TRANSIT) {
      if (train.getCurrentStation() != station || station.getPresentTrain() != train)
        return List.of();
      ticks = 0;
    } else {
      if (!train.navigation.isActive() || train.navigation.destination != station) return List.of();
      ticks = arrivalTicks < 0 ? -1 : Math.max(1, arrivalTicks);
    }
    var out = new ArrayList<TrainDeparturePrediction>();
    out.add(new TrainDeparturePrediction(train, ticks, predictionTitle(), station.name));
    if (futureStops.isEmpty()) return out;
    var seen = new HashSet<String>();
    seen.add(station.name);
    var dimensions = train.getPresentDimensions();
    var stations = new LinkedHashMap<String, com.simibubi.create.content.trains.station.GlobalStation>();
    for (var stop : train.graph.getPoints(EdgePointType.STATION)) {
      var dimension = stop.getBlockEntityDimension();
      if (!stop.assembling && dimension != null && dimensions.contains(dimension) && RailwaysCompat.stationEnabled(stop))
        stations.putIfAbsent(stop.name, stop);
    }
    for (var future : futureStops) {
      var candidates = future.literal() ? stations.containsKey(future.filter())
          ? List.of(stations.get(future.filter())) : List.<com.simibubi.create.content.trains.station.GlobalStation>of()
          : stations.values();
      for (var stop : candidates) {
        if (out.size() >= ScheduleForecast.MAX_STOPS) return out;
        if (!future.literal() && !future.pattern().matcher(stop.name).matches() || !seen.add(stop.name)) continue;
        out.add(new TrainDeparturePrediction(train, -1, UserText.userInput(future.title()), stop.name));
      }
    }
    return out;
  }

  private MutableComponent predictionTitle() {
    return UserText.userInput(ScheduleForecast.title(forecastSteps, currentEntry, schedule.cyclic, currentTitle));
  }

  @Override
  public MutableComponent getWaitingStatus(Level level) {
    return schedule == null
            || currentEntry >= schedule.entries.size()
            || state != State.POST_TRANSIT
        ? Component.empty()
        : super.getWaitingStatus(level);
  }
}
