package com.guidedrailways;
/** Closed-loop effort/drag/brake scenarios, without launching a server. */
public final class DriverModelCheck {
    private static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static double force(double speed,double limit,double rpm,double mass,int notch,boolean emergency,boolean hold,double dt){return DriverModel.force(speed,limit,rpm,mass,notch,emergency,hold,6,2.5,4,.6,dt);}
    static double run(double speed,double limit,double rpm,double mass,int notch,boolean emergency,boolean hold,double seconds,double dt){
        for(int i=0;i<Math.round(seconds/dt);i++){
            double f=force(speed,limit,rpm,mass,notch,emergency,hold,dt),next=speed+f/mass*dt;
            check(Double.isFinite(next),"finite integration");check(Math.abs(next-speed)<=8*dt+1e-9,"bounded acceleration or braking");
            if(emergency||hold||notch<=0)check(speed*next>=-1e-10,"braking never reverses travel");
            speed=next;
        }return speed;
    }
    public static void main(String[] args){
        for(double dt:new double[]{.05,1d/60,1d/120})for(double mass:new double[]{80,600,5000})for(int direction:new int[]{-1,1}){
            double limit=direction*12.8;
            double low=run(0,limit,-256,mass,1,false,false,2,dt),high=run(0,limit,256,mass,4,false,false,2,dt);
            check(Math.abs(high)>Math.abs(low)*2,"notches change effort/acceleration, independent of RPM sign");
            double lowFinal=run(low,limit,256,mass,1,false,false,240,dt),highFinal=run(high,limit,256,mass,4,false,false,240,dt);
            check(Math.abs(lowFinal-limit)<.001&&Math.abs(highFinal-limit)<.001,"all positive notches share the RPM ceiling");
            check(force(direction*6,limit,256,mass,1,false,false,dt)*direction>0,"reducing to P1 does not brake toward one quarter speed");
            check(force(direction*15,limit,256,mass,1,false,false,dt)==0,"powered overspeed tapers without secret braking");
            double coast=run(direction*6,limit,256,mass,0,false,false,1,dt);
            check(Math.abs(coast)>5&&Math.abs(coast)<6,"centre coasts despite available stress input");
            double b1=run(direction*6,limit,256,mass,-1,false,false,1,dt),b4=run(direction*6,limit,256,mass,-4,false,false,1,dt),estop=run(direction*6,limit,0,mass,4,true,false,1,dt);
            check(Math.abs(estop)<Math.abs(b4)&&Math.abs(b4)<Math.abs(b1),"four graded brakes and independent stronger emergency brake");
            check(Math.abs(run(direction*6,limit,0,mass,4,false,true,10,dt))<1e-10,"power-loss/authority-loss hold stops without RPM");
            check(force(0,0,256,mass,4,false,false,dt)==0,"neutral reverser cannot produce traction");
            check(force(0,limit,0,mass,4,false,false,dt)==0,"no phantom power");
            check(Math.abs(run(direction*6,limit,256,mass,-1,false,false,10,dt))<1e-10,"B1 eventually stops, with no speed inversion");
        }
        for(int n=-4;n<=4;n++){
            check(DriverModel.step(n,1)==Math.min(4,n+1),"one positive detent per step");
            check(DriverModel.step(n,-1)==Math.max(-4,n-1),"one negative detent per step");
            check(DriverModel.canChangeDirection(0,n)==(n<=0),"reverser requires no traction");
            check(!DriverModel.canChangeDirection(.151,n),"moving reverser interlock");
        }
        check(!DriverModel.canChangeDirection(Double.NaN,0),"invalid speed cannot unlock reverser");
        check(force(Double.NaN,4,256,80,1,false,false,.05)==0,"invalid state rejected");
        check(force(1,4,256,80,-99,false,false,.05)==force(1,4,256,80,-4,false,false,.05),"notch range clamped");
        System.out.println("DriverModelCheck: "+checks+" assertions passed");
    }
}
