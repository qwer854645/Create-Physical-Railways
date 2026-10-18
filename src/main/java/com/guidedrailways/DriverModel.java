package com.guidedrailways;
/** Notches control effort, never a fraction of target speed. */
public final class DriverModel {
    public static final int MAX_NOTCH=4;
    public static final double STOP_SPEED=.15;
    private DriverModel(){}
    public static boolean canChangeDirection(double speed,int notch){return Double.isFinite(speed)&&Math.abs(speed)<=STOP_SPEED&&notch<=0;}
    public static int step(int notch,int delta){return Math.clamp(notch+Integer.signum(delta),-MAX_NOTCH,MAX_NOTCH);}
    public static double force(double speed,double ceiling,double rpm,double mass,int notch,boolean emergency,boolean hold,double forcePerRpm,double acceleration,double braking,double coasting,double dt){
        if(!Double.isFinite(speed)||!Double.isFinite(ceiling)||!Double.isFinite(rpm)||!Double.isFinite(mass)||mass<=0||dt<=0||!Double.isFinite(dt))return 0;
        notch=Math.clamp(notch,-MAX_NOTCH,MAX_NOTCH);
        if(emergency)return DriveModel.coastForce(speed,mass,braking*2,dt);
        if(hold)return DriveModel.coastForce(speed,mass,braking,dt);
        if(notch<0)return DriveModel.coastForce(speed,mass,braking*(-notch/(double)MAX_NOTCH),dt);
        if(notch==0||Math.abs(rpm)<.001||Math.abs(ceiling)<1e-8)return DriveModel.coastForce(speed,mass,coasting,dt);
        // Defensive fallback; the reverser itself is interlocked against moving changes.
        if(ceiling*speed<0&&Math.abs(speed)>STOP_SPEED)return DriveModel.coastForce(speed,mass,braking,dt);
        double error=(ceiling-speed)*Math.signum(ceiling);
        if(error<=0)return 0; // Reducing power never silently applies the service brake.
        double effort=notch/(double)MAX_NOTCH;
        double limit=Math.min(Math.abs(rpm)*forcePerRpm,mass*acceleration)*effort;
        return Math.copySign(Math.min(error*mass*2,Math.min(limit,mass*error/dt)),ceiling);
    }
}
