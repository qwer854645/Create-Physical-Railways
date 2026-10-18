package com.guidedrailways;
public final class SpeedSettingCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(SpeedSetting.fromRatio(.05)==128,"legacy default remains 12.8 blocks/s at 256 RPM");
        for(double ratio:new double[]{.001,.05,.1,1})check(Math.abs(SpeedSetting.target(256,SpeedSetting.fromRatio(ratio))-ratio*256)<=.05,"legacy config migrates within the GUI tenth precision");
        for(int setting:new int[]{1,128,200,640,2560}){
            check(Math.abs(SpeedSetting.target(256,setting)-setting/10.0)<1e-12,"full RPM reaches configured target");
            check(SpeedSetting.target(0,setting)==0,"a speed setting never supplies power");
            for(int rpm=1;rpm<=256;rpm++){
                double target=SpeedSetting.target(rpm,setting);
                check(Math.abs(target/(setting/10.0)-rpm/256.0)<1e-12,"every input RPM scales proportionally");
                check(target==SpeedSetting.target(-rpm,setting),"reversed inputs retain the same target magnitude");
                check(target<=SpeedSetting.target(256,setting),"targets never exceed configured maximum at normal Create RPM");
            }
        }
        check(SpeedSetting.target(128,200)==10&&SpeedSetting.target(64,200)==5,"20 blocks/s setting gives 10 at 128 RPM and 5 at 64 RPM");
        for(int invalid:new int[]{Integer.MIN_VALUE,-1,0,2561,Integer.MAX_VALUE})check(!SpeedSetting.valid(invalid)&&SpeedSetting.target(256,invalid)==0,"invalid or malicious settings cannot create unbounded propulsion");
        check(SpeedSetting.target(Double.NaN,128)==0&&SpeedSetting.target(Double.POSITIVE_INFINITY,128)==0,"invalid RPM cannot propagate non-finite forces");
        int[] gears={20,40,80,100,128,160,200,256};
        check(SpeedSetting.validGears(SpeedSetting.MAX,gears),"eight ascending absolute target speeds are accepted");
        for(double available:new double[]{0,3,6.4,12.8,25.6})for(int notch=1;notch<=8;notch++){
            check(SpeedSetting.gearCeiling(available,notch,gears)==Math.min(available,gears[notch-1]/10.0),"manual gear is an absolute speed capped only by available RPM");
        }
        check(SpeedSetting.gearCeiling(25.6,3,gears)==8&&SpeedSetting.gearCeiling(12.8,3,gears)==8,"P3 remains 8 blocks/s at both 256 and 128 RPM, never 4 blocks/s");
        check(SpeedSetting.gearCeiling(6.4,3,gears)==6.4,"64 RPM cannot exceed its 6.4 blocks/s power ceiling");
        check(!SpeedSetting.validGears(SpeedSetting.MAX,new int[]{20,40,80}),"truncated network or saved gear lists are invalid");
        int[] descending=gears.clone();descending[4]=1;
        check(!SpeedSetting.validGears(SpeedSetting.MAX,descending),"higher notches cannot request lower target speeds");
        for(int max:new int[]{1,128,256,2560})check(SpeedSetting.validGears(max,SpeedSetting.defaultGears(max))&&SpeedSetting.defaultGears(max)[7]==max,"default profiles retain the configured cap even at very low maxima");
        for(int direction:new int[]{-1,1})for(double dt:new double[]{.05,1d/120}){
            double speed=direction*20;
            for(int i=0;i<20/dt;i++){
                double force=DriverModel.manualForce(speed,direction*8,256,600,3,false,false,6,2.5,4,.6,dt);
                double next=speed+force/600*dt;
                check(Math.abs(next-speed)<=4*dt+1e-9&&next*direction>0,"manual downshift brakes within service limits without reversing");speed=next;
            }
            check(Math.abs(Math.abs(speed)-8)<.061,"manual downshift converges to its configured absolute speed");
            double f1=DriverModel.manualForce(0,8,256,600,1,false,false,6,2.5,4,.6,dt);
            double f8=DriverModel.manualForce(0,8,256,600,8,false,false,6,2.5,4,.6,dt);
            check(f1==f8,"manual notch denotes speed rather than an effort percentage");
            check(DriverModel.manualForce(0,8,0,600,3,false,false,6,2.5,4,.6,dt)==0,"a configured gear cannot supply power to an unpowered bogie");
        }
        System.out.println("SpeedSettingCheck: "+checks+" assertions passed");
    }
}
