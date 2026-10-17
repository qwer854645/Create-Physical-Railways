package com.guidedrailways;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class TrainConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SPEED_PER_RPM,FORCE_PER_RPM,ACCELERATION,BRAKING,STRESS_MASS;
    static {
        var b=new ModConfigSpec.Builder();
        SPEED_PER_RPM=b.comment("Target blocks per second per input RPM").defineInRange("speedPerRpm",.04,.001,1);
        FORCE_PER_RPM=b.comment("Maximum traction force per input RPM per powered bogie").defineInRange("forcePerRpm",6.0,0,10000);
        ACCELERATION=b.comment("Maximum powered acceleration in blocks/s^2").defineInRange("maxAcceleration",2.5,.1,50);
        BRAKING=b.comment("Service brake deceleration in blocks/s^2").defineInRange("brakeDeceleration",4,.1,50);
        STRESS_MASS=b.comment("Supported mass per stress impact unit").defineInRange("massPerStress",32.0,1,10000);
        SPEC=b.build();
    }
}
