package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Vector3dc;
/** Gameplay release criteria only: never adds force or changes a body pose or velocity. */
public final class DerailmentModel {
    private DerailmentModel(){}
    public static double lateralCurvature(Vector3dc before,Vector3dc after,Vector3dc tangent,Vector3dc up,double chord){
        if(!(chord>1e-8))return 0;
        var side=new Vector3d(tangent).cross(up);
        if(side.lengthSquared()<1e-12)return 0;
        return Math.abs(new Vector3d(after).sub(before).dot(side.normalize()))/chord;
    }
    public static double curveRisk(double speed,double curvature,double height,double lateralLimit){
        if(!Double.isFinite(speed)||!Double.isFinite(curvature)||!Double.isFinite(height)||!(lateralLimit>0))return 0;
        return speed*speed*Math.max(0,curvature)*Math.max(.75,height)/(lateralLimit*1.5);
    }
    public static final class Exposure {
        public double curveSeconds,impactSeconds;
        public String update(double curveRisk,double lateralSpeed,double impactLimit,double delay,double dt){
            if(!(dt>0)||!Double.isFinite(dt))return null;
            curveSeconds=curveRisk>1?curveSeconds+dt:Math.max(0,curveSeconds-2*dt);
            impactSeconds=lateralSpeed>impactLimit?impactSeconds+dt:Math.max(0,impactSeconds-2*dt);
            if(lateralSpeed>impactLimit*2||impactSeconds>=.075)return "lateral_impact";
            if(curveSeconds>=delay&&curveRisk>1)return "curve_overload";
            return null;
        }
    }
}
