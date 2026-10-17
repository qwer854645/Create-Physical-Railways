package com.guidedrailways;
public final class DriveModelCheck {
    private static int checks;
    private static void check(boolean condition,String text){checks++;if(!condition)throw new AssertionError(text);}
    private static double f(double v,double rpm,double mass,boolean brake){return DriveModel.force(v,rpm,mass,brake,.04,6,2.5,4);}
    public static void main(String[] args){
        check(f(5,0,100,false)==0,"zero input must coast");
        check(f(11,256,100,false)==0,"overspeed must coast without an implicit brake");
        check(f(0,256,100,false)>0,"forward launch");check(f(0,-256,100,false)<0,"reverse launch");
        for(double mass:new double[]{20,100,1000})for(double rpm:new double[]{16,64,256})for(double v:new double[]{-10,-1,0,1,10}){
            double force=f(v,rpm,mass,false);check(Double.isFinite(force),"finite force");
            check(Math.abs(force)<=Math.abs(rpm)*6+1e-8,"finite power budget");check(Math.abs(force)<=mass*2.5+1e-8,"acceleration cap");
            double brake=f(v,rpm,mass,true);check(brake*v<=0,"brake must be passive");check(Math.abs(brake)<=mass*4+1e-8,"brake cap");
            check(Math.abs(force+f(-v,-rpm,mass,false))<1e-8,"symmetric forward and reverse");
        }
        check(f(0,64,1000,false)/1000<f(0,64,100,false)/100,"load reduces acceleration at fixed traction budget");
        check(f(Double.NaN,64,100,false)==0,"bad velocity rejected");
        check(f(0,64,0,false)==0,"zero mass rejected");
        double speed=0;for(int i=0;i<2000;i++)speed+=f(speed,256,100,false)/100*.005;
        check(Math.abs(speed-10.24)<.01,"unloaded drive approaches target");
        double positive=speed;for(int i=0;i<1000;i++)speed+=f(speed,0,100,true)/100*.005;
        check(speed>=0&&speed<.001&&speed<positive,"brake converges without reversing");
        System.out.println("DriveModelCheck: "+checks+" assertions passed");
    }
}
