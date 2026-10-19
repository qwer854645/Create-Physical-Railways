package com.guidedrailways;
public final class RailwaysCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("ballast")){
            for(var gauge:RailGauge.values()){
                var cells=BogieCollision.cells(gauge);int width=cells.stream().mapToInt(c->Math.abs(c.x())).max().orElse(1);
                for(var c:cells)if(c.y()==0&&Math.abs(c.x())==width&&Math.abs(c.z())==1)System.out.println("gauge="+gauge.ordinal()+",part="+c.part());
            }
            return;
        }
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
        for(var gauge:new RailGauge[]{RailGauge.NARROW,RailGauge.WIDE,RailGauge.MONORAIL}){
            var cells=BogieCollision.cells(gauge);var ids=new java.util.HashSet<Integer>();
            check(cells.stream().anyMatch(c->c.part()==BogieCollision.CENTRE_PART&&c.x()==0&&c.y()==0&&c.z()==0),"every gauge retains its block entity at the physical centre");
            for(var cell:cells){check(ids.add(cell.part())&&cell.part()<=127,"collision part ids are unique and fit block properties");for(var box:cell.boxes())check(box.x0()>=0&&box.y0()>=0&&box.z0()>=0&&box.x1()<=1&&box.y1()<=1&&box.z1()<=1,"scaled collision remains partitioned inside occupied cells");}
            for(var box:BogieCollision.boxes(gauge)){
                double x=(box.x0()+box.x1())*.5,y=(box.y0()+box.y1())*.5,z=(box.z0()+box.z1())*.5;
                check(cells.stream().anyMatch(c->c.boxes().stream().anyMatch(v->v.contains(x-c.x(),y-c.y(),z-c.z()))),"partitioning cannot lose the centre of a scaled collision primitive");
            }
        }
        RailPlacement.Route beam=d->new RailPlacement.Point(new org.joml.Vector3d(0,0,d),new org.joml.Vector3d(0,0,1),new org.joml.Vector3d(0,1,0));
        double monoRide=BogieGeometry.ride(RailGauge.MONORAIL),monoPivot=BogieGeometry.pivotHeight(BogieGeometry.MOUNT_HEIGHT_VERSION,RailGauge.MONORAIL);
        var mono=RailPlacement.bogie(beam,0,monoRide,1,monoPivot);
        check(Math.abs(mono.origin().y-1)<1e-8,"mono model origin aligns horizontal side rollers with the one-block beam");
        check(Math.abs(mono.origin().y+BogieGeometry.plateOffset(RailGauge.MONORAIL)+.5-2)<1e-8,"monorail keeps the mounting platform on the same integral grid");
        check(Math.abs(mono.pivot().y-1.75)<1e-8,"monorail bearing agrees with the mounting plate underside");
        check(RailCouplerMode.choose(false,false,true,true,true,true)==1,"two stopped free ends can couple");
        check(RailCouplerMode.choose(true,true,true,true,true,true)==2,"an existing direct link can uncouple");
        check(RailCouplerMode.choose(false,true,true,true,true,true)==0,"trackside coupling cannot close a loop in the same train");
        check(RailCouplerMode.choose(false,false,false,true,true,true)==0,"moving trains cannot be forcibly joined");
        check(RailCouplerMode.choose(false,false,true,false,true,true)==0,"missing or occupied sockets cannot be joined");
        check(RailCouplerMode.choose(true,true,true,true,true,false)==0,"coupling-only setting never uncouples");
        check(RailCouplerMode.choose(false,false,true,true,false,true)==0,"uncoupling-only setting never couples");
        var mass=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/guidedrailways/physics_block_properties/bogie_collision.json"))).getAsJsonObject().getAsJsonObject("overrides");
        for(var gauge:RailGauge.values()){
            var cells=BogieCollision.cells(gauge);int outer=cells.stream().mapToInt(c->Math.abs(c.x())).max().orElse(1);int count=0;
            for(var c:cells)if(c.y()==0&&Math.abs(c.x())==outer&&Math.abs(c.z())==1){
                String massKey="gauge="+gauge.ordinal()+",part="+c.part();check(mass.has(massKey)&&mass.getAsJsonObject(massKey).get("sable:mass").getAsDouble()==8,"ballast remains symmetric at the four outer corners for every geometry profile");count++;
            }
            check(count==4,"every bogie retains four equal ballast cells");
        }
        CargoHookStatus.disable("railways");check(CargoHookStatus.ready("logistics")&&CargoHookStatus.ready("dock"),"an absent railways adapter cannot disable unrelated cargo compatibility");
        System.out.println("RailwaysCheck: "+checks+" assertions passed");
    }
}
