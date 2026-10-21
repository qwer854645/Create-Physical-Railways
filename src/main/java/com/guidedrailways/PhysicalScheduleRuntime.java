package com.guidedrailways;

import com.simibubi.create.content.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
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
    return List.of(new TrainDeparturePrediction(train, ticks, predictionTitle(), station.name));
  }

  private MutableComponent predictionTitle() {
    if (!currentTitle.isBlank()) return UserText.userInput(currentTitle);
    // Match Create's fallback: the next scheduled destination supplies a blank journey title.
    for (int i = 1; i < schedule.entries.size(); i++) {
      int entry = currentEntry + i;
      if (entry >= schedule.entries.size() && !schedule.cyclic) break;
      var instruction = schedule.entries.get(entry % schedule.entries.size()).instruction;
      if (instruction instanceof DestinationInstruction destination)
        return UserText.userInput(destination.getFilter().replace("*", "").trim());
    }
    return Component.empty();
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
