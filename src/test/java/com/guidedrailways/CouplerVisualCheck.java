package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
public final class CouplerVisualCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static List<Object> parts(CouplerVisual.Layout layout){
        var out=new ArrayList<Object>();for(var box:layout.boxes())out.add(Map.of("material",box.material().name(),"min",new double[]{box.x0(),box.y0(),box.z0()},"max",new double[]{box.x1(),box.y1(),box.z1()},"pivot",new double[]{layout.pivot().x,layout.pivot().y,layout.pivot().z},"rotation",new double[]{layout.rotation().x,layout.rotation().y,layout.rotation().z,layout.rotation().w}));return out;
    }
    public static void main(String[] args)throws Exception{
        var random=new Random(717);
        for(int i=0;i<5000;i++){
            var qa=new Quaterniond().rotationXYZ(random.nextDouble(),random.nextDouble()*6,random.nextDouble());
            var qb=new Quaterniond().rotationXYZ(random.nextDouble(),random.nextDouble()*6,random.nextDouble());
            var pa=new Vector3d(16000000.5,.5,16000000.5);var pb=new Vector3d(pa).add(1024,0,1024);
            var wa=new Vector3d(random.nextDouble()*20,random.nextDouble(),random.nextDouble()*20);var wb=new Vector3d(wa).add(.1,0,.1);
            var joint=CouplerVisual.joint(wa,wb);
            var ta=new Vector3d(joint).sub(wa).rotate(new Quaterniond(qa).conjugate()).add(pa);
            var tb=new Vector3d(joint).sub(wb).rotate(new Quaterniond(qb).conjugate()).add(pb);
            var up=new Vector3d(0,1,0).rotate(new Quaterniond(qa).conjugate());
            var a=CouplerVisual.layout(pa,ta,up,1,.73);var b=CouplerVisual.layout(pb,tb,new Vector3d(0,1,0).rotate(new Quaterniond(qb).conjugate()),1,.73);
            var headA=new Vector3d(0,0,a.length()).rotate(a.rotation()).add(pa).sub(pa).rotate(qa).add(wa);
            var headB=new Vector3d(0,0,b.length()).rotate(b.rotation()).add(pb).sub(pb).rotate(qb).add(wb);
            check(headA.distance(headB)<2e-8,"rendered tips meet across independent body rotations and large plot coordinates");
            check(a.rotation().isFinite()&&b.rotation().isFinite(),"finite frames");
            for(var box:a.boxes())check(box.x1()>box.x0()&&box.y1()>box.y0()&&box.z1()>box.z0(),"nonzero render boxes");
        }
        for(double length:new double[]{0,1e-12,.05,.63,.73,.83,1.73,2.73}){
            var a=CouplerVisual.layout(new Vector3d(),new Vector3d(0,0,length),new Vector3d(0,0,1),0,.73);
            check(a.rotation().isFinite(),"degenerate up fallback");check(Math.abs(a.extension()-(length-.73))<1e-9,"buffer extension");
        }
        var scenes=new ArrayList<Object>();
        for(int i=0;i<6;i++){
            String name=new String[]{"Uncoupled","Straight / locked","Curve / articulated","Slope transition","Pull / extended","Push / compressed"}[i];
            var pivot=new Vector3d(0,0,0);var tip=new Vector3d(0,0,.73);double lock=i==0?0:1;
            if(i==2)tip.set(.35,0,.73);if(i==3)tip.set(.15,.25,.73);if(i==4)tip.z=.83;if(i==5)tip.z=.63;
            var parts=new ArrayList<Object>(parts(CouplerVisual.layout(pivot,tip,new Vector3d(0,1,0),lock,.73)));
            if(i!=0){var other=new Vector3d(tip).mul(2);if(i==2)other.x=0;if(i==3)other.y=.25;
                parts.addAll(parts(CouplerVisual.layout(other,tip,new Vector3d(0,1,0),lock,.73)));
                parts.add(Map.of("material","DARK","min",new double[]{-.3125,-.25,-.25},"max",new double[]{.3125,.25,0},"pivot",new double[]{other.x,other.y,other.z},"rotation",new double[]{0,1,0,0}));
            }
            parts.add(Map.of("material","DARK","min",new double[]{-.3125,-.25,-.25},"max",new double[]{.3125,.25,0},"pivot",new double[]{0,0,0},"rotation",new double[]{0,0,0,1}));
            // The same inner socket as the static block model, in each housing's local frame.
            var sockets=new ArrayList<Object>();
            for(var part:parts)if(part instanceof Map<?,?> m && "DARK".equals(m.get("material")) && m.get("min") instanceof double[] min && min[0]==-.3125)
                sockets.add(Map.of("material","IRON","min",new double[]{-.25,-.1875,-.125},"max",new double[]{.25,.1875,.25},"pivot",m.get("pivot"),"rotation",m.get("rotation")));
            parts.addAll(sockets);
            scenes.add(Map.of("name",name,"parts",parts));
        }
        var root=Path.of("build/previews");Files.createDirectories(root);
        Files.writeString(root.resolve("coupler-scenes.json"),new GsonBuilder().setPrettyPrinting().create().toJson(scenes));
        Files.writeString(root.resolve("coupler-rest.json"),new Gson().toJson(parts(CouplerVisual.layout(new Vector3d(),new Vector3d(0,0,.73),new Vector3d(0,1,0),0,.73))));
        System.out.println("CouplerVisualCheck passed: "+checks+" checks; preview geometry exported");
    }
}
