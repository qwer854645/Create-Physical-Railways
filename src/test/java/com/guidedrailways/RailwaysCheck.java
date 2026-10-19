package com.guidedrailways;
public final class RailwaysCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        for(var gauge:RailGauge.values()){
            check(RailGauge.read(gauge.name())==gauge,"profile round trip");
            check(gauge.accepts("railways:universal")== (gauge!=RailGauge.MONORAIL),"universal rail does not turn into a monorail beam");
            check(!gauge.accepts("other:unknown"),"unknown track types are not silently standard gauge");
        }
        check(RailGauge.read("")==RailGauge.STANDARD,"old saves retain standard gauge");
        check(RailGauge.STANDARD.accepts("create:standard"),"all materials of the native standard type are supported");
        check(!RailGauge.STANDARD.accepts("railways:narrow_gauge"),"standard bogie cannot enter narrow rail");
        check(!RailGauge.STANDARD.accepts("railways:wide_gauge"),"standard bogie cannot enter wide rail");
        check(!RailGauge.STANDARD.accepts("railways:monorail"),"standard bogie cannot enter a beam");
        check(RailGauge.NARROW.accepts("railways:narrow_gauge")&&RailGauge.WIDE.accepts("railways:wide_gauge")&&RailGauge.MONORAIL.accepts("railways:monorail"),"special track types are distinct");
        System.out.println("RailwaysCheck: "+checks+" assertions passed");
    }
}
