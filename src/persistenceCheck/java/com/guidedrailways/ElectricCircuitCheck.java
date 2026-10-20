package com.guidedrailways;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;

/** Verify the release jar's result/current orientation using actual circuit and load objects. */
public final class ElectricCircuitCheck {
  public static void main(String[] args) {
    var bus = new AttachedNode(0, "guidedrailways:test");
    var ground = new AttachedNode(1, "guidedrailways:test");
    var otherTrain = new AttachedNode(0, "guidedrailways:other");
    if (bus.equals(ground) || bus.equals(otherTrain)) throw new AssertionError("train buses alias");
    var builder = new CircuitBuilder(java.util.Set.of());
    builder.addNode(bus);
    builder.addNode(ground);
    builder.ground(ground, 10);
    builder.connect(
        bus, ground, ElectricalProperties.resistor(ElectricDriveModel.resistance(3000, 40000)));
    for (double voltage : new double[] {0, 500, 3000, -3000}) {
      var results =
          new SimulationResults(
              new double[] {voltage, 0},
              1,
              new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>(),
              builder,
              null);
      double actualV = results.getVoltageAt(bus, ground);
      double actualI = results.getCurrentThrough(bus, ground);
      double expected = Math.abs(voltage) < 1000 ? 0 : 34000;
      double watts = ElectricDriveModel.delivered(actualV, actualI, 1000);
      if (Math.abs(watts - expected) > 1e-6)
        throw new AssertionError("CEE result/current mismatch " + watts);
    }
    System.out.println(
        "ElectricCircuitCheck: actual CEE load/current, bus isolation and blackout results passed; no world started");
  }
}
