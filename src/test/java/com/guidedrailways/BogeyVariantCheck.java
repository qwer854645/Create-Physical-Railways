package com.guidedrailways;
import java.util.*;
import org.joml.Vector3d;
public final class BogeyVariantCheck {
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    public static void main(String[] args){
        var ids=new HashSet<String>();
        for(var v:BogeyVariant.values()){
            check(ids.add(v.id)&&BogeyVariant.read(v.ordinal())==v&&BogeyVariant.find(v.id)==v,"saved indices and style IDs are unambiguous");
            if(v==BogeyVariant.DEFAULT)continue;
            var boxes=BogieCollision.boxes(RailGauge.STANDARD,v);var cells=BogieCollision.cells(RailGauge.STANDARD,v);var parts=new HashSet<Integer>();
            check(cells.stream().anyMatch(c->c.part()==BogieCollision.CENTRE_PART&&c.x()==0&&c.y()==0&&c.z()==0),"native frame retains the central block entity");
            for(var cell:cells){check(parts.add(cell.part())&&cell.part()<=127,"every occupied native collision cell has a unique encodable part");
                for(var b:cell.boxes())check(b.x0()>=0&&b.y0()>=0&&b.z0()>=0&&b.x1()<=1&&b.y1()<=1&&b.z1()<=1,"physics shape remains inside its owning voxel cell");
            }
            var offset=BogiePairContact.offset(RailGauge.STANDARD,v);var half=BogiePairContact.half(RailGauge.STANDARD,v);
            for(var b:boxes)for(double x:new double[]{b.x0(),(b.x0()+b.x1())/2,b.x1()})for(double y:new double[]{b.y0(),(b.y0()+b.y1())/2,b.y1()})for(double z:new double[]{b.z0(),(b.z0()+b.z1())/2,b.z1()}){
                check(cells.stream().anyMatch(c->c.boxes().stream().anyMatch(q->q.contains(x-c.x(),y-c.y(),z-c.z()))),"partitioning never cuts away frame or wheel collision at a cell boundary");
                var point=new Vector3d(x-.5,y-.5,z-.5).sub(offset);
                check(Math.abs(point.x)<=half.x()+1e-7&&Math.abs(point.y)<=half.y()+1e-7&&Math.abs(point.z)<=half.z()+1e-7,"anti-interlocking pair envelope encloses the selected model's collision");
            }
            check(Math.abs(boxes.stream().mapToDouble(BogieCollision.Box::y1).max().orElseThrow()-1)<1e-7,"native top platform keeps the existing mount height");
        }
        check(BogeyVariant.read(-1)==BogeyVariant.DEFAULT&&BogeyVariant.read(999)==BogeyVariant.DEFAULT,"unknown persisted indices fall back safely");
        check(BogeyVariant.find("railways:large_create_styled_0_12_0")==null,"uncalibrated large bogeys cannot silently use a small collision profile");
        for(var gauge:RailGauge.values())check(BogieCollision.boxes(gauge,BogeyVariant.DEFAULT)==BogieCollision.boxes(gauge),"legacy standard/narrow/wide/mono geometry is retained");
        System.out.println("BogeyVariantCheck: "+checks+" assertions passed");
    }
}
