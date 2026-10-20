package com.guidedrailways;

import com.george_vi.electroenergetics.config.CResistances;
import java.lang.reflect.Field;

/** CEE 1.0 used resistive train loads; 1.1.1 introduced configurable power loads. */
final class ElectroLoadConfig {
  record Fields(Field acceleration, Field cruise, boolean watts) {}

  private static final Fields FIELDS = fields(CResistances.class);

  static Fields fields(Class<?> type) {
    try {
      try {
        return new Fields(
            type.getField("electricTrainAccelerationPowerConsumption"),
            type.getField("electricTrainCruisePowerConsumption"),
            true);
      } catch (NoSuchFieldException oldRelease) {
        return new Fields(
            type.getField("electricTrainAccelerationResistance"),
            type.getField("electricTrainCruiseResistance"),
            false);
      }
    } catch (NoSuchFieldException unsupported) {
      throw new IllegalStateException("Unsupported CEE train load configuration", unsupported);
    }
  }

  static double watts(CResistances config, boolean accelerating, double voltage) {
    try {
      var field = accelerating ? FIELDS.acceleration() : FIELDS.cruise();
      double value = ((CResistances.ConfigDouble) field.get(config)).get();
      return FIELDS.watts() ? value : ElectricDriveModel.resistiveDemand(voltage, value);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("CEE train load configuration is inaccessible", error);
    }
  }

  private ElectroLoadConfig() {}
}
