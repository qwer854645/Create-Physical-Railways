package com.guidedrailways;
/** Pixel-independent mapping shared by the lever, speed slider and offline checks. */
public final class GearEditorModel {
    private GearEditorModel(){}
    public static int selection(double y,double top,double bottom){
        if(!Double.isFinite(y)||bottom<=top)return 0;
        return (int)Math.clamp(Math.round((bottom-y)/(bottom-top)*(DriverModel.MAX_NOTCH-1)),0,DriverModel.MAX_NOTCH-1);
    }
    public static double fraction(int speed,int maximum){
        return maximum<=SpeedSetting.MIN?0:Math.clamp((speed-SpeedSetting.MIN)/(double)(maximum-SpeedSetting.MIN),0,1);
    }
    public static int speed(double fraction,int maximum){
        if(!Double.isFinite(fraction))return SpeedSetting.MIN;
        maximum=Math.clamp(maximum,SpeedSetting.MIN,SpeedSetting.MAX);
        return SpeedSetting.MIN+(int)Math.round(Math.clamp(fraction,0,1)*(maximum-SpeedSetting.MIN));
    }
    public static int betweenNeighbours(int speed,int selected,int[] gears){
        int low=selected==0?SpeedSetting.MIN:gears[selected-1];
        int high=selected==gears.length-1?SpeedSetting.MAX:gears[selected+1];
        return Math.clamp(speed,low,high);
    }
}
