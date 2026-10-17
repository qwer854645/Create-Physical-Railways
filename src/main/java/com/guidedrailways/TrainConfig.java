package com.guidedrailways;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class TrainConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SPEED_PER_RPM,FORCE_PER_RPM,ACCELERATION,BRAKING,COASTING,STRESS_MASS;
    public static final ModConfigSpec.BooleanValue DETAILED_LOGGING,PERFORMANCE_LOGGING;
    static {
        var b=new ModConfigSpec.Builder();
        SPEED_PER_RPM=b.comment("Target blocks per second per input RPM").defineInRange("speedPerRpm",.05,.001,1);
        FORCE_PER_RPM=b.comment("Maximum traction force per input RPM per powered bogie").defineInRange("forcePerRpm",6.0,0,10000);
        ACCELERATION=b.comment("Maximum powered acceleration in blocks/s^2").defineInRange("maxAcceleration",2.5,.1,50);
        BRAKING=b.comment("Service brake deceleration in blocks/s^2").defineInRange("brakeDeceleration",4,.1,50);
        COASTING=b.comment("Unpowered coasting resistance in blocks/s^2; zero restores free coasting, not a parking brake").defineInRange("coastDeceleration",.6,0,20);
        STRESS_MASS=b.comment("Supported mass per stress impact unit").defineInRange("massPerStress",32.0,1,10000);
        DETAILED_LOGGING=b.comment("Verbose joint diagnostics; turn off outside debugging").define("detailedLogging",false);
        PERFORMANCE_LOGGING=b.comment("Java guidance timing only; excludes native solver and rendering").define("performanceLogging",false);
        SPEC=b.build();
    }
}
