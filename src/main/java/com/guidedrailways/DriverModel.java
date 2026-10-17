package com.guidedrailways;
/** Manual command policy; leaves acceleration and braking to the existing impulse drive. */
public final class DriverModel {
    public static final int MAX_SPEED=40;
    private DriverModel(){}
    public static double target(int direction,double setting,double rpm,double speedPerRpm){
        if(direction==0||!Double.isFinite(setting)||!Double.isFinite(rpm)||!Double.isFinite(speedPerRpm)||speedPerRpm<=0)return 0;
        return Math.signum(direction)*Math.min(Math.clamp(setting,0,MAX_SPEED),Math.abs(rpm)*speedPerRpm);
    }
    public static double force(double speed,double target,double rpm,double mass,int brake,boolean neutral,double forcePerRpm,double acceleration,double braking,double coasting,double dt){
        if(!Double.isFinite(speed)||!Double.isFinite(target)||!Double.isFinite(rpm)||!Double.isFinite(mass)||mass<=0||dt<=0||!Double.isFinite(dt))return 0;
        if(brake>0)return DriveModel.coastForce(speed,mass,braking*(brake==2?2:1),dt);
        if(neutral||Math.abs(rpm)<.001)return DriveModel.coastForce(speed,mass,coasting,dt);
        // Reversing requests first brake to a standstill; never reverse the velocity directly.
        if(target*speed<0&&Math.abs(speed)>.15)return DriveModel.coastForce(speed,mass,braking,dt);
        double error=target-speed;
        if(Math.abs(error)<1e-8)return 0;
        if(error*speed<0)return Math.copySign(mass*Math.min(braking,Math.min(Math.abs(error)*2,Math.abs(error)/dt)),error);
        double limit=Math.min(Math.abs(rpm)*forcePerRpm,mass*acceleration);
        return Math.copySign(Math.min(Math.abs(error)*mass*2,Math.min(limit,mass*Math.abs(error)/dt)),error);
    }
}
