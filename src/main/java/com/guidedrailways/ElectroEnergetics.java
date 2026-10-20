package com.guidedrailways;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

/** One electrical load per physical consist, powered through CEE's real pantograph devices. */
final class ElectroEnergetics implements TrainElectricity.Bridge {
  private static final class Supply {
    final Set<UUID> members;
    final AttachedNode bus, ground;
    List<VehiclePhysics.Unit> units = List.of();
    List<PantographBlockEntity> pantographs = List.of();
    long scanAt, measuredAt = Long.MIN_VALUE;
    double voltage = 3000, watts;

    Supply(Set<UUID> members, UUID id) {
      this.members = Set.copyOf(members);
      String owner = "guidedrailways:" + id;
      bus = new AttachedNode(0, owner);
      ground = new AttachedNode(1, owner);
    }
  }

  private final Map<ServerLevel, Map<UUID, Supply>> supplies = new WeakHashMap<>();
  private final Map<ServerLevel, List<Supply>> inFlight = new WeakHashMap<>();

  static void register() {
    var bridge = new ElectroEnergetics();
    TrainElectricity.bridge = bridge;
    NeoForge.EVENT_BUS.addListener(bridge::graph);
    NeoForge.EVENT_BUS.addListener(bridge::finished);
  }

  private static boolean live(VehiclePhysics.Unit u) {
    return !u.carrier.isRemoved() && !u.bogie.isRemoved() && !u.mount.isRemoved();
  }

  @Override
  public void refresh(ServerLevel level) {
    var old = supplies.getOrDefault(level, Map.of());
    var next = new HashMap<UUID, Supply>();
    for (var group : TrainConsists.get(level).groups()) {
      var units = group.units().stream().filter(ElectroEnergetics::live).toList();
      if (units.isEmpty()) continue;
      UUID key = Collections.min(group.members());
      var state = old.get(key);
      if (state == null || !state.members.equals(group.members()))
        state = new Supply(group.members(), key);
      state.units = units;
      if (level.getGameTime() >= state.scanAt) {
        var pantographs = new ArrayList<PantographBlockEntity>();
        for (var body : group.bodies().values()) {
          if (body.isRemoved()) continue;
          for (var holder : body.getPlot().getLoadedChunks())
            for (var entity : holder.getChunk().getBlockEntities().values())
              if (entity instanceof PantographBlockEntity p && !p.isRemoved()) pantographs.add(p);
        }
        state.pantographs = List.copyOf(pantographs);
        state.scanAt = level.getGameTime() + 20;
      }
      boolean contact = state.pantographs.stream().anyMatch(p -> !p.isRemoved() && p.extended);
      double watts =
          contact
                  && state.measuredAt != Long.MIN_VALUE
                  && level.getGameTime() >= state.measuredAt
                  && level.getGameTime() - state.measuredAt <= 2
              ? state.watts / units.size()
              : 0;
      boolean equipped = state.pantographs.stream().anyMatch(p -> !p.isRemoved());
      for (var unit : units) {
        unit.electricWatts = watts;
        unit.electricEquipped = equipped;
      }
      next.put(key, state);
    }
    supplies.put(level, next);
  }

  private void graph(AddToElectricGraphEvent event) {
    var active = new ArrayList<Supply>();
    for (var state : supplies.getOrDefault(event.level, Map.of()).values()) {
      var pantographs =
          state.pantographs.stream()
              .filter(
                  p ->
                      !p.isRemoved()
                          && p.extended
                          && event.level.hasChunkAt(p.getBlockPos())
                          && event.level.getBlockEntity(p.getBlockPos()) == p)
              .toList();
      if (pantographs.isEmpty() || state.units.stream().noneMatch(ElectroEnergetics::live))
        continue;
      double demand = .1;
      for (var u : state.units) {
        if (!live(u) || u.detached || u.mount.braking || Math.abs(u.mount.getSpeed()) > .001)
          continue;
        var command = u.command;
        if (command != null
            && !command.owner().isRemoved()
            && command.notch() > 0
            && !command.hold()
            && !command.emergency()) {
          boolean accelerating =
              Math.abs(u.speed) + .1 < Math.abs(VehicleTraction.requestedSpeed(u));
          demand +=
              ElectroLoadConfig.watts(
                  CEEConfigs.server().resistanceValues, accelerating, state.voltage);
        }
      }
      event.builder.ground(state.ground, 10);
      event.builder.connect(
          state.bus,
          state.ground,
          ElectricalProperties.resistor(ElectricDriveModel.resistance(state.voltage, demand)));
      for (var p : pantographs)
        event.builder.connect(
            new InWorldNode(0, p.getBlockPos()),
            state.bus,
            ElectricalProperties.resistor(
                CEEConfigs.server().resistanceValues.wireResistance.get()));
      active.add(state);
    }
    inFlight.put(event.level, List.copyOf(active));
  }

  private void finished(FinishElectricSimulationEvent event) {
    var batch = inFlight.remove(event.level);
    if (batch == null) return;
    for (var state : batch) {
      double voltage = Math.abs(event.results.getVoltageAt(state.bus, state.ground));
      double current = Math.abs(event.results.getCurrentThrough(state.bus, state.ground));
      state.voltage = voltage;
      state.watts =
          ElectricDriveModel.delivered(
              voltage, current, CEEConfigs.server().voltageValues.trainMinVoltage.get());
      state.measuredAt = event.level.getGameTime();
    }
  }

  @Override
  public void unload(ServerLevel level) {
    supplies.remove(level);
    inFlight.remove(level);
  }
}
