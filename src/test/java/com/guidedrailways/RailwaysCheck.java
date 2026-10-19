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
        var claims=new SwitchReservations();var key=new SwitchReservations.Key(java.util.UUID.randomUUID(),12);var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();
        check(claims.claim(key,a,1,2,0),"first approach reserves the switch");
        check(!claims.claim(key,b,1,2,10),"another train cannot steal a live passage");
        check(!claims.claim(key,a,1,3,10),"a train cannot split itself across different branches");
        check(claims.claim(key,a,2,1,20),"the same consist can reverse through its reserved passage");
        check(claims.owns(key,a,1,2,59)&&!claims.owns(key,a,1,2,60),"heartbeat ownership expires");
        check(claims.claim(key,b,1,3,60),"a cleared or removed train eventually releases the switch");
        check(RailwayStopModel.force(10,100,Double.POSITIVE_INFINITY,10,4,.05)==100,"unblocked travel keeps its existing traction");
        check(RailwayStopModel.force(10,100,2,10,4,.05)<0,"a blocked switch applies braking before the junction");
        check(RailwayStopModel.force(0,100,0,10,4,.05)==0,"a stopped train cannot power through a closed switch");
        check(RailwayStopModel.force(-1,-100,0,10,4,.05)==200,"terminal braking opposes reverse travel without reversing momentum");
        var edge=new ScheduleRoute.Edge(1,2);
        ScheduleRoute.Network buffered=new ScheduleRoute.Network(){
            public double length(ScheduleRoute.Edge e){return 10;}
            public java.util.List<ScheduleRoute.Edge> exits(ScheduleRoute.Edge e){return java.util.List.of();}
            public boolean clear(ScheduleRoute.Edge e,double from,double to){return !(Math.min(from,to)<=5&&Math.max(from,to)>=5);}
        };
        var after=new ScheduleRoute.Goal(a,"after",edge,8);var before=new ScheduleRoute.Goal(b,"before",edge,3);
        check(ScheduleRoute.find(buffered,edge,0,java.util.List.of(after))==null,"a target beyond a buffer on the same edge is unreachable");
        check(ScheduleRoute.find(buffered,edge,0,java.util.List.of(before))!=null,"a station before the buffer remains reachable");
        check(ScheduleRoute.find(buffered,edge,6,java.util.List.of(after))!=null,"a buffer behind the train does not block forward navigation");
        System.out.println("RailwaysCheck: "+checks+" assertions passed");
    }
}
