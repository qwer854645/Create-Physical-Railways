package com.guidedrailways;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Create {@code TravellingPoint.steer} scoring, without the Minecraft graph types.
 * LEFT/RIGHT/NONE map to target dots -1 / +1 / 0 against {@code (exit × up) · entry}.
 */
public final class BranchSelect {
    public enum Steer { NONE(0), LEFT(-1), RIGHT(1); final float targetDot; Steer(float t){targetDot=t;} }

    private BranchSelect(){}

    /** Actual motion wins over throttle; near rest retain direction to avoid jitter. */
    public static boolean reverseTravel(double speed,double rpm,boolean previous){
        if(Double.isFinite(speed)&&Math.abs(speed)>.02)return speed<0;
        if(Double.isFinite(speed)&&Math.abs(speed)<.005&&Double.isFinite(rpm)&&Math.abs(rpm)>.001)return rpm<0;
        return previous;
    }
    public static boolean exitsIncreasing(Vector3dc increasing,Vector3dc bodyForward,boolean reverse){
        return increasing.dot(bodyForward)*(reverse?-1:1)>=0;
    }
    /** Infer Create-style steer from a preferred world heading relative to the exit trajectory. */
    public static Steer fromHeading(Vector3dc exitDirection,Vector3dc up,Vector3dc preferredHeading){
        var side=new Vector3d(exitDirection).cross(up);
        if(side.lengthSquared()<1e-12)return Steer.NONE;
        double lean=side.normalize().dot(preferredHeading);
        if(lean>.2)return Steer.RIGHT;
        if(lean<-.2)return Steer.LEFT;
        return Steer.NONE;
    }

    /**
     * @param exitDirection current edge direction at the leaving end ({@code getDirection(false)})
     * @param entryDirections candidate edge start directions ({@code getDirection(true)})
     * @return index of best candidate, or -1 if empty
     */
    public static int steer(Steer direction,Vector3dc exitDirection,Vector3dc up,Vector3dc[] entryDirections){
        if(entryDirections==null||entryDirections.length==0)return -1;
        var normal=new Vector3d(exitDirection).cross(up);
        double closest=Double.MAX_VALUE;int best=0;
        for(int i=0;i<entryDirections.length;i++){
            double diff=Math.abs(direction.targetDot-normal.dot(entryDirections[i]));
            if(diff>closest)continue;
            closest=diff;best=i;
        }
        return best;
    }
}
