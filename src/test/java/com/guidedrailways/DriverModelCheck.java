package com.guidedrailways;
/** Closed-loop longitudinal scenarios, not a substitute for client/native-physics tests. */
public final class DriverModelCheck {
    static int checks;
    static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    static double force(double speed,double target,double rpm,double mass,int brake,boolean neutral,double dt){return DriverModel.force(speed,target,rpm,mass,brake,neutral,6,2.5,4,.6,dt);}
    static double run(double speed,double target,double rpm,double mass,int brake,boolean neutral,double seconds,double dt){
        for(int i=0;i<seconds/dt;i++){
            double f=force(speed,target,rpm,mass,brake,neutral,dt);double next=speed+f/mass*dt;
            check(Double.isFinite(next),"finite response");
            check(Math.abs(next-speed)<=8*dt+1e-9,"bounded braking/acceleration");
            if(brake>0||neutral||Math.abs(rpm)<.001)check(speed*next>=-1e-10,"no reversal from braking or rolling resistance");
            speed=next;
        }return speed;
    }
    public static void main(String[] args){
        for(double dt:new double[]{1.0/20,1.0/60,1.0/120})for(double mass:new double[]{80,600,5000})for(int direction:new int[]{-1,1}){
            double target=DriverModel.target(direction,40,-256,.05);
            check(Math.abs(target-direction*12.8)<1e-12,"input magnitude caps requested speed independently of input RPM sign");
            double speed=run(0,target,256,mass,0,false,80,dt);
            check(Math.abs(speed-target)<.001,"reaches available speed when powered long enough");
            double lower=run(speed,direction*3,256,mass,0,false,10,dt);
            check(Math.abs(lower-direction*3)<.001,"reducing setting decelerates without reversing");
            double coast=run(lower,0,256,mass,0,true,1,dt);
            check(Math.abs(coast)<Math.abs(lower)&&Math.abs(coast)>1,"neutral coasts despite input RPM");
            double service=run(speed,0,0,mass,1,false,2,dt),emergency=run(speed,0,0,mass,2,false,2,dt);
            check(Math.abs(emergency)<Math.abs(service),"emergency stops more rapidly than service brake without stress power");
            check(Math.abs(run(speed,0,0,mass,1,false,10,dt))<1e-10,"service brake stops completely");
            check(Math.abs(run(speed,-target,256,mass,0,false,80,dt)+target)<.001,"direction change stops then accelerates in reverse");
            check(Math.abs(run(speed,0,256,mass,0,false,10,dt))<1e-6,"zero target stops instead of acting like neutral");
            check(Math.abs(run(speed,target,0,mass,1,false,10,dt))<1e-10,"power loss automatic brake is independent of RPM");
        }
        check(DriverModel.target(0,40,256,.05)==0,"neutral has no traction target");
        check(DriverModel.target(1,40,0,.05)==0,"no phantom power");
        check(DriverModel.target(1,-100,256,.05)==0,"negative speed setting rejected");
        check(DriverModel.target(1,Double.NaN,256,.05)==0,"invalid target rejected");
        check(force(Double.NaN,4,256,80,0,false,.05)==0,"invalid speed rejected");
        System.out.println("DriverModelCheck: "+checks+" assertions passed");
    }
}
