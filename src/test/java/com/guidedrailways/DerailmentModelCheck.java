package com.guidedrailways;
import org.joml.Vector3d;
public final class DerailmentModelCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var up=new Vector3d(0,1,0);
        for(double radius:new double[]{4,8,16,64})for(double angle:new double[]{0,.3,1.4}){
            double step=.01;
            var a=new Vector3d(Math.cos(angle-step),0,Math.sin(angle-step));
            var b=new Vector3d(Math.cos(angle+step),0,Math.sin(angle+step));
            var tangent=new Vector3d(Math.cos(angle),0,Math.sin(angle));
            double chord=2*radius*Math.sin(step);
            double curvature=DerailmentModel.lateralCurvature(a,b,tangent,up,chord);
            check(Math.abs(curvature-1/radius)<1e-10,"circle curvature equals inverse radius");
            check(Math.abs(DerailmentModel.lateralCurvature(a,b,new Vector3d(tangent).negate(),up,chord)-curvature)<1e-10,"reverse traversal has same curve risk");
        }
        check(DerailmentModel.lateralCurvature(new Vector3d(0,0,1),new Vector3d(0,.1,.995),new Vector3d(0,0,1),up,1)==0,"vertical grade change is not a lateral bend");
        // Defaults: lateralLimit=14, delay=0.35, impact=4.5 — R16 full speed stays under, R8 overloads.
        check(DerailmentModel.curveRisk(12.8,1.0/16,1.5,14)<1,"reference R16 at full default speed remains below limit");
        check(DerailmentModel.curveRisk(12.8,1.0/8,1.5,14)>1,"reference R8 at full speed overloads");
        check(DerailmentModel.curveRisk(12.8,1.0/16,3.5,14)>1,"high centre of mass increases overturn risk");
        check(DerailmentModel.curveRisk(-12.8,1.0/8,1.5,14)==DerailmentModel.curveRisk(12.8,1.0/8,1.5,14),"reverse speed symmetry");
        check(DerailmentModel.curveRisk(0,1,3,14)==0,"stationary curve cannot derail from curve overload");
        check(DerailmentModel.curveRisk(100,0,3,14)==0,"straight speed alone cannot cause derailment");
        for(double dt:new double[]{.005,.025,.05}){
            var e=new DerailmentModel.Exposure();String reason=null;double elapsed=0;
            while(reason==null&&elapsed<1){reason=e.update(2,0,4.5,.35,dt);elapsed+=dt;}
            check("curve_overload".equals(reason)&&elapsed>=.35-1e-8&&elapsed<=.35+dt+1e-8,"sustained overload releases at configured delay");
            e=new DerailmentModel.Exposure();
            for(int j=0;j<100;j++)check(e.update(.8,.5,4.5,.35,dt)==null,"normal travel keeps guidance");
        }
        var e=new DerailmentModel.Exposure();
        check(e.update(2,0,4.5,.35,.025)==null,"one curve spike is filtered");
        check(e.update(0,0,4.5,.35,.025)==null&&e.curveSeconds==0,"safe travel recovers exposure");
        check("lateral_impact".equals(e.update(0,9.5,4.5,.35,.025)),"severe lateral impact releases immediately");
        e=new DerailmentModel.Exposure();
        check(e.update(0,5,4.5,.35,.025)==null,"brief moderate side velocity is filtered");
        e.update(0,5,4.5,.35,.025);e.update(0,5,4.5,.35,.025);
        check("lateral_impact".equals(e.update(0,5,4.5,.35,.025)),"sustained sideways motion releases");
        check(new DerailmentModel.Exposure().update(0,0,4.5,.35,0)==null,"invalid step ignored");
        System.out.println("DerailmentModelCheck: "+checks+" assertions passed");
    }
}
