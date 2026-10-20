package com.guidedrailways;
import java.util.*;
import org.joml.Vector3d;
public final class BogeyVariantCheck {
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var mass=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/guidedrailways/physics_block_properties/bogie_collision.json"))).getAsJsonObject().getAsJsonObject("overrides");
        var ids=new HashSet<String>();
        for(var v:BogeyVariant.values()){
            check(ids.add(v.id+"/"+v.size)&&BogeyVariant.read(v.ordinal())==v&&BogeyVariant.find(v.id,v.size)==v,"saved indices and style IDs are unambiguous");
            if(v==BogeyVariant.DEFAULT)continue;
            var boxes=BogieCollision.boxes(RailGauge.STANDARD,v);var cells=BogieCollision.cells(RailGauge.STANDARD,v);var parts=new HashSet<Integer>();
            check(cells.stream().anyMatch(c->c.part()==BogieCollision.CENTRE_PART&&c.x()==0&&c.y()==0&&c.z()==0),"native frame retains the central block entity");
            for(var cell:cells){check(parts.add(cell.part())&&cell.part()<=127,"every occupied native collision cell has a unique encodable part");
                for(var b:cell.boxes())check(b.x0()>=0&&b.y0()>=0&&b.z0()>=0&&b.x1()<=1&&b.y1()<=1&&b.z1()<=1,"physics shape remains inside its owning voxel cell");
            }
            var offset=BogiePairContact.offset(RailGauge.STANDARD,v);var half=BogiePairContact.half(RailGauge.STANDARD,v);
            for(var b:boxes)for(double x:new double[]{b.x0(),b.x1()})for(double y:new double[]{b.y0(),b.y1()})for(double z:new double[]{b.z0(),b.z1()}){
                check(cells.stream().anyMatch(c->c.boxes().stream().anyMatch(q->q.contains(x-c.x(),y-c.y(),z-c.z()))),"partitioning never cuts away frame or wheel collision at a cell boundary");
                var point=new Vector3d(x-.5,y-.5,z-.5).sub(offset);
                check(Math.abs(point.x)<=half.x()+1e-7&&Math.abs(point.y)<=half.y()+1e-7&&Math.abs(point.z)<=half.z()+1e-7,"anti-interlocking pair envelope encloses the selected model's collision");
            }
            check(v.ride+boxes.stream().mapToDouble(BogieCollision.Box::y1).max().orElseThrow()-.5<=v.mountTop-.25+1e-7,"mount underside clears the complete measured animation envelope");
            double total=48;for(var cell:cells)if(cell.part()!=BogieCollision.CENTRE_PART){var props=mass.getAsJsonObject("style="+v.ordinal()+",part="+cell.part());check(props!=null,"every native outer cell has an explicit mass, independent of legacy ballast indices");total+=props.get("sable:mass").getAsDouble();}
            check(Math.abs(total-(cells.size()>1?80:48))<1e-6,"native body mass remains bounded when the model has more collision cells");
            check(v.tread>0&&v.half>=.5&&Double.isFinite(v.ride),"each wheel geometry has finite drive and guidance dimensions");
            RailPlacement.Route straight=d->new RailPlacement.Point(new Vector3d(0,0,d),new Vector3d(0,0,1),new Vector3d(0,1,0));
            var fit=RailPlacement.bogie(straight,0,v.ride,v.half,v.pivotHeight(2,v.gauge));
            check(Math.abs(fit.origin().y-v.ride)<1e-8&&Math.abs(fit.pivot().y-(v.mountTop-.25))<1e-8,"each profile places its own wheel height and mounting bearing on the expected grid");
            check(Math.abs((fit.front()-fit.rear())-2*v.half)<1e-5,"long native wheelbases receive sufficient front/rear rail length");
        }
        check(BogeyVariant.read(-1)==BogeyVariant.DEFAULT&&BogeyVariant.read(999)==BogeyVariant.DEFAULT,"unknown persisted indices fall back safely");
        check(BogeyVariant.find("railways:large_create_style_0_12_0","create:large")!=null,"largest six-axle steam style has its own large profile");
        check(BogeyVariant.values().length==40,"all 39 native style/size combinations plus legacy geometry are registered");
        check(BogeyVariant.read(1)==BogeyVariant.FREIGHT&&BogeyVariant.read(5)==BogeyVariant.Y25,"previous native save indices remain stable");
        var small=BogeyVariant.defaultFor(RailGauge.STANDARD);var large=BogeyVariant.find("create:standard","create:large");
        RailPlacement.Route straight=d->new RailPlacement.Point(new Vector3d(0,0,d),new Vector3d(0,0,1),new Vector3d(0,1,0));
        var mixed=RailPlacement.carriage(straight,List.of(new Vector3d(0,0,-4),new Vector3d(0,large.mountTop-small.mountTop,4)),0,new Vector3d(0,0,1),List.of(new RailPlacement.Dimensions(small.ride,small.half,small.pivotHeight(2,small.gauge)),new RailPlacement.Dimensions(large.ride,large.half,large.pivotHeight(2,large.gauge))));
        check(mixed.bogies().size()==2&&mixed.rotation().isFinite(),"a shared carriage can fit different native mounting heights without assigning one profile to both bogeys");
        for(var gauge:RailGauge.values())check(BogieCollision.boxes(gauge,BogeyVariant.DEFAULT)==BogieCollision.boxes(gauge),"legacy standard/narrow/wide/mono geometry is retained");
        System.out.println("BogeyVariantCheck: "+checks+" assertions passed");
    }
}
