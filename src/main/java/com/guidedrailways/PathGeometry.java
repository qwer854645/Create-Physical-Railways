package com.guidedrailways;
import java.util.function.DoubleFunction;
import org.joml.Vector3d;
import org.joml.Vector3dc;
/** All arguments are dimensionless path parameters. Direction is derived from the same positions as projection. */
public final class PathGeometry {
    private PathGeometry(){}
    public static Vector3d tangent(DoubleFunction<Vector3d> position,double parameter){
        double t=Math.clamp(parameter,0,1),lo=Math.max(0,t-.0001),hi=Math.min(1,t+.0001);
        var direction=position.apply(hi).sub(position.apply(lo));
        if(!direction.isFinite()||direction.lengthSquared()<1e-16)throw new IllegalArgumentException("Degenerate track tangent");
        return direction.normalize();
    }
    public static boolean beyondEndpoint(double t,Vector3dc position,Vector3dc query,Vector3dc increasingTangent,double allowance){
        double along=new Vector3d(query).sub(position).dot(increasingTangent);
        return (t==0&&along< -allowance)||(t==1&&along>allowance);
    }
}
