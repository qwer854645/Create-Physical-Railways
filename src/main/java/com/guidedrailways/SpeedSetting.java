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
    public static int[] defaultGears(int maximum){
        int[] result=new int[DriverModel.MAX_NOTCH];
        for(int i=0;i<result.length;i++)result[i]=Math.max(MIN,(int)Math.round(maximum*(i+1.0)/result.length));
        return result;
    }
    public static boolean validGears(int maximum,int[] gears){
        if(!valid(maximum)||gears==null||gears.length!=DriverModel.MAX_NOTCH)return false;
        int previous=MIN;
        for(int gear:gears){if(!valid(gear)||gear<previous||gear>maximum)return false;previous=gear;}
        return true;
    }
    public static double gearCeiling(double available,int notch,int[] gears){
        if(!Double.isFinite(available)||available<0)return 0;
        if(notch<=0||!validGears(MAX,gears))return available;
        return Math.min(available,gears[Math.min(notch,gears.length)-1]/10.0);
    }
}
