package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;
import java.util.*;
/** Shared render geometry: offline previews and the client renderer use the same moving parts. */
public final class CouplerVisual {
    public enum Material { IRON,DARK,BRASS }
    public record Box(Material material,double x0,double y0,double z0,double x1,double y1,double z1){}
    public record Layout(Vector3d pivot,Quaterniond rotation,List<Box> boxes,double length,double extension){}
    private CouplerVisual(){}
    public static Vector3d pivot(int x,int z){return new Vector3d(.5-x*.25,.5,.5-z*.25);}
    public static Vector3d restTip(int x,int z){return new Vector3d(.5+x*.48,.5,.5+z*.48);}
    public static Vector3d joint(Vector3dc anchorA,Vector3dc anchorB){return new Vector3d(anchorA).add(anchorB).mul(.5);}
    public static Layout layout(Vector3dc pivot,Vector3dc tip,Vector3dc up,double lock,double restLength){
        var delta=new Vector3d(tip).sub(pivot);double length=delta.length();
        if(!delta.isFinite()||!Double.isFinite(length)||!Double.isFinite(lock)||!Double.isFinite(restLength)||!up.isFinite())throw new IllegalArgumentException("invalid coupler visual pose");
        var direction=length>1e-8?delta.div(length):new Vector3d(0,0,1);var normal=new Vector3d(up);
        if(new Vector3d(direction).cross(normal).lengthSquared()<1e-8)normal.set(Math.abs(direction.y)<.9?new Vector3d(0,1,0):new Vector3d(1,0,0));
        var rotation=RailPlacement.orientation(direction,normal);var boxes=new ArrayList<Box>();lock=Math.clamp(lock,0,1);
        double depth=Math.min(.28,Math.max(.02,length*.65)),shaft=Math.max(0,length-depth+.02);
        if(shaft>.001){
            boxes.add(new Box(Material.IRON,-.125,-.125,0,.125,.125,shaft));
            double sleeve=Math.min(.24,shaft);
            boxes.add(new Box(Material.DARK,-.15625,-.15625,0,.15625,.15625,sleeve));
            if(sleeve>.04)boxes.add(new Box(Material.BRASS,-.171875,-.171875,sleeve-.035,.171875,.171875,sleeve));
        }
        boxes.add(new Box(Material.DARK,-.25,-.1875,length-depth,.25,.1875,length-depth*.65));
        boxes.add(new Box(Material.IRON,-.25,-.1875,length-depth*.7,.0625,.1875,length));
        double jaw=.075*(1-lock);
        boxes.add(new Box(Material.IRON,.0625+jaw,-.1875,length-depth*.35,.25+jaw,.1875,length));
        double lift=.10*(1-lock);
        boxes.add(new Box(Material.BRASS,-.15625,.1875+lift,length-depth*.75,.15625,.25+lift,length-depth*.35));
        return new Layout(new Vector3d(pivot),rotation,List.copyOf(boxes),length,length-restLength);
    }
}
