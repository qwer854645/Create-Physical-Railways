package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
/** Pure initial engagement geometry. There is no scripted pulling force. */
public final class CouplerGeometry {
    public static final double HALF_STROKE=.1,MAX_GAP=.6;
    public record Engagement(Vector3d point,Quaterniond frame){}
    public record Spring(double stiffness,double damping,double maxForce){}
    private CouplerGeometry(){}
    public static Engagement engage(Vector3dc a,Vector3dc facingA,Vector3dc upA,Vector3dc b,Vector3dc facingB,Vector3dc upB){
        if(!a.isFinite()||!b.isFinite()||!facingA.isFinite()||!facingB.isFinite()||facingA.lengthSquared()<.5||facingB.lengthSquared()<.5)throw new IllegalArgumentException("车钩姿态无效");
        var fa=new Vector3d(facingA).normalize();var fb=new Vector3d(facingB).normalize();
        if(fa.dot(fb)>-.5)throw new IllegalArgumentException("请让两个车钩的钩头相向");
        var delta=new Vector3d(b).sub(a);
        if(delta.length()>MAX_GAP)throw new IllegalArgumentException("请把两节车靠近，钩头间距需小于 0.6 格");
        if(delta.dot(fa)<-.05||new Vector3d(delta).negate().dot(fb)<-.05)throw new IllegalArgumentException("车钩已经交错，请先把车辆拉开");
        var x=new Vector3d(fa).sub(fb).normalize();var up=new Vector3d(upA).add(upB);
        if(!up.isFinite()||up.lengthSquared()<.1)throw new IllegalArgumentException("请先把车体摆正再连挂");
        up.normalize();if(Math.abs(delta.dot(up))>.25)throw new IllegalArgumentException("两端车钩高度相差过大");
        var transverse=new Vector3d(delta).fma(-delta.dot(x),x);
        if(transverse.length()>.3)throw new IllegalArgumentException("两端车钩左右错位过大");
        var z=new Vector3d(x).cross(up);if(z.lengthSquared()<.1)throw new IllegalArgumentException("车钩姿态无效");z.normalize();var y=new Vector3d(z).cross(x).normalize();
        return new Engagement(new Vector3d(a).add(b).mul(.5),new Quaterniond().setFromNormalized(new Matrix3d(x,y,z)));
    }
    public static Spring spring(double ma,double mb){
        if(!Double.isFinite(ma)||!Double.isFinite(mb)||ma<=0||mb<=0)throw new IllegalArgumentException("车体质量无效");
        double reduced=1/(1/ma+1/mb),omega=2*Math.PI*2;
        return new Spring(reduced*omega*omega,2*reduced*omega,reduced*400);
    }
}
