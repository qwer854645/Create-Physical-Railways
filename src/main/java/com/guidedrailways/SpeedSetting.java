package com.guidedrailways;
/** Console target speed at Create's 256 RPM, represented in tenths of a block/second. */
public final class SpeedSetting {
    public static final int MIN=1,MAX=2560;
    private SpeedSetting(){}
    public static boolean valid(int tenths){return tenths>=MIN&&tenths<=MAX;}
    public static int fromRatio(double ratio){
        if(!Double.isFinite(ratio)||ratio<=0)return 128;
        return (int)Math.clamp(Math.round(ratio*2560),MIN,MAX);
    }
    public static double target(double rpm,int tenths){
        return !Double.isFinite(rpm)||!valid(tenths)?0:Math.abs(rpm)*tenths/2560.0;
    }
}
