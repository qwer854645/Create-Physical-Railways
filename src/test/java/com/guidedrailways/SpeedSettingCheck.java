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
        System.out.println("SpeedSettingCheck: "+checks+" assertions passed");
    }
}
