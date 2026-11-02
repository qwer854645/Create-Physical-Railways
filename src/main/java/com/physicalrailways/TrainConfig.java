package com.physicalrailways;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrainConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.DoubleValue MAX_TARGET_SPEED,
      FORCE_PER_RPM,
      ACCELERATION,
      BRAKING,
      COASTING,
      STRESS_MASS,
      DERAIL_LATERAL,
      DERAIL_DELAY,
      DERAIL_IMPACT;
  public static final ModConfigSpec.BooleanValue DETAILED_LOGGING, PERFORMANCE_LOGGING, DERAILMENT;

  static {
    var b = new ModConfigSpec.Builder();
    MAX_TARGET_SPEED =
        b.comment(
                "Maximum target blocks per second at 256 input RPM; other RPM scales proportionally")
            .translation("config.physicalrailways.maxTargetSpeedAt256")
            .defineInRange("maxTargetSpeedAt256", 25.6, .1, 256);
    FORCE_PER_RPM =
        b.comment("Maximum traction force per input RPM per powered bogie")
            .translation("config.physicalrailways.forcePerRpm")
            .defineInRange("forcePerRpm", 6.0, 0, 10000);
    ACCELERATION =
        b.comment("Maximum powered acceleration in blocks/s^2")
            .translation("config.physicalrailways.maxAcceleration")
            .defineInRange("maxAcceleration", 2.5, .1, 50);
    BRAKING =
        b.comment("Service brake deceleration in blocks/s^2")
            .translation("config.physicalrailways.brakeDeceleration")
            .defineInRange("brakeDeceleration", 4, .1, 50);
    COASTING =
        b.comment(
                "Unpowered coasting resistance in blocks/s^2; zero restores free coasting, not a parking brake")
            .translation("config.physicalrailways.coastDeceleration")
            .defineInRange("coastDeceleration", .6, 0, 20);
    STRESS_MASS =
        b.comment("Supported mass per stress impact unit")
            .translation("config.physicalrailways.massPerStress")
            .defineInRange("massPerStress", 32.0, 1, 10000);
    DERAILMENT =
        b.comment(
                "Enable curve overload and lateral impact guide release; missing track always releases guidance")
            .translation("config.physicalrailways.derailmentEnabled")
            .define("derailmentEnabled", false);
    DERAIL_LATERAL =
        b.comment(
                "Allowed lateral acceleration at a 1.5-block centre of mass height, in blocks/s^2; higher allows faster bends")
            .translation("config.physicalrailways.derailLateralAcceleration")
            .defineInRange("derailLateralAcceleration", 14.0, 1, 100);
    DERAIL_DELAY =
        b.comment(
                "Accumulated curve overload seconds before release; safe travel recovers twice as fast")
            .translation("config.physicalrailways.derailOverloadSeconds")
            .defineInRange("derailOverloadSeconds", .35, .05, 5);
    DERAIL_IMPACT =
        b.comment("Residual sideways axle speed causing impact release, in blocks/s")
            .translation("config.physicalrailways.derailImpactSpeed")
            .defineInRange("derailImpactSpeed", 4.5, .5, 50);
    DETAILED_LOGGING =
        b.comment("Verbose joint diagnostics; turn off outside debugging")
            .translation("config.physicalrailways.detailedLogging")
            .define("detailedLogging", false);
    PERFORMANCE_LOGGING =
        b.comment("Java guidance timing only; excludes native solver and rendering")
            .translation("config.physicalrailways.performanceLogging")
            .define("performanceLogging", false);
    SPEC = b.build();
  }

  public static double speedPerRpm() {
    return MAX_TARGET_SPEED.get() / 256.0;
  }
}
