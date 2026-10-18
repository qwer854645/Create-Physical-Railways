package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;
import org.joml.Matrix3d;
import java.util.*;
/** Geometry-only placement planner. Route distance is signed metres along one connected track. */
public final class RailPlacement {
    public record Point(Vector3d position,Vector3d tangent,Vector3d up){}
    public interface Route { Point at(double distance); }
    public record Bogie(Vector3d origin,Quaterniond rotation,Vector3d pivot,double front,double rear){}
    public record Car(Vector3d position,Quaterniond rotation,List<Bogie> bogies){}
    public record RayHit(double distanceSquared,double rayFraction,double segmentFraction){}
    private RailPlacement(){}
    public static Quaterniond orientation(Vector3dc forward,Vector3dc up){
        var z=new Vector3d(forward).normalize();var x=new Vector3d(up).cross(z);
        if(x.lengthSquared()<1e-12)throw new IllegalArgumentException("message.guidedrailways.rail.invalid_direction");
        x.normalize();var y=new Vector3d(z).cross(x).normalize();
        return new Quaterniond().setFromNormalized(new Matrix3d(x,y,z));
    }
    /** Closest points on two finite segments, including perpendicular aim and endpoints. */
    public static RayHit raySegment(Vector3dc start,Vector3dc end,Vector3dc a,Vector3dc b){
        var u=new Vector3d(end).sub(start);var v=new Vector3d(b).sub(a);var w=new Vector3d(start).sub(a);
        double aa=u.lengthSquared(),bb=u.dot(v),cc=v.lengthSquared(),dd=u.dot(w),ee=v.dot(w);
        double s=0,t=0,den=aa*cc-bb*bb;
        if(aa<1e-12&&cc<1e-12)return new RayHit(start.distanceSquared(a),0,0);
        if(aa<1e-12)t=Math.clamp(ee/cc,0,1);
        else if(cc<1e-12)s=Math.clamp(-dd/aa,0,1);
        else{
            s=den>1e-12?Math.clamp((bb*ee-cc*dd)/den,0,1):0;
            t=(bb*s+ee)/cc;
            if(t<0){t=0;s=Math.clamp(-dd/aa,0,1);}
            else if(t>1){t=1;s=Math.clamp((bb-dd)/aa,0,1);}
        }
        return new RayHit(new Vector3d(start).fma(s,u).distanceSquared(new Vector3d(a).fma(t,v)),s,t);
    }
    public static Bogie bogie(Route route,double centre,double ride,double halfWheelbase){
        double lo=0,hi=halfWheelbase*1.65;
        Point a=route.at(centre+hi),b=route.at(centre-hi);
        // Find the available symmetric interval first: a short curve can still fit a rigid wheelbase.
        if(a==null||b==null){
            double available=0,missing=hi;
            for(int i=0;i<26;i++){double mid=(available+missing)*.5;if(route.at(centre+mid)!=null&&route.at(centre-mid)!=null)available=mid;else missing=mid;}
            hi=available;a=route.at(centre+hi);b=route.at(centre-hi);
        }
        if(a==null||b==null||a.position.distance(b.position)<2*halfWheelbase-1e-5)throw new IllegalArgumentException("message.guidedrailways.rail.no_room");
        for(int i=0;i<24;i++){
            double mid=(lo+hi)*.5;var front=route.at(centre+mid);var rear=route.at(centre-mid);
            if(front==null||rear==null)throw new IllegalArgumentException("message.guidedrailways.rail.disconnected");
            if(front.position.distance(rear.position)<2*halfWheelbase)lo=mid;else hi=mid;
        }
        double half=(lo+hi)*.5;a=route.at(centre+half);b=route.at(centre-half);
        var up=new Vector3d(a.up).add(b.up).normalize();var forward=new Vector3d(a.position).sub(b.position);
        var rotation=orientation(forward,up);up.set(0,1,0).rotate(rotation);
        var origin=new Vector3d(a.position).add(b.position).mul(.5).fma(ride,up);
        return new Bogie(origin,rotation,new Vector3d(origin).fma(.375,up),centre+half,centre-half);
    }
    /** Fit one rigid carrier to every mounting seat; invalid plans never mutate a body. */
    public static Car carriage(Route route,List<Vector3d> seats,int selected,Vector3dc localForward,double ride,double halfWheelbase){
        if(seats.isEmpty()||selected<0||selected>=seats.size())throw new IllegalArgumentException("message.guidedrailways.rail.no_mount");
        var arcs=new double[seats.size()];var anchor=seats.get(selected);
        int pa=0,pb=0;double spread=0;
        for(int i=0;i<seats.size();i++)for(int j=i+1;j<seats.size();j++)if(seats.get(i).distanceSquared(seats.get(j))>spread){spread=seats.get(i).distanceSquared(seats.get(j));pa=i;pb=j;}
        var along=new Vector3d(localForward).normalize();
        if(spread>1e-8){along.set(seats.get(pb)).sub(seats.get(pa)).normalize();double dot=along.dot(localForward);if(dot<0||(Math.abs(dot)<1e-6&&(Math.abs(along.z)>Math.abs(along.x)?along.z<0:along.x<0)))along.negate();}
        for(int i=0;i<arcs.length;i++)arcs[i]=new Vector3d(seats.get(i)).sub(anchor).dot(along);
        Quaterniond rotation=new Quaterniond();Vector3d position=new Vector3d();var bogies=new ArrayList<Bogie>();
        for(int iteration=0;iteration<40;iteration++){
            bogies.clear();for(double arc:arcs)bogies.add(bogie(route,arc,ride,halfWheelbase));
            var worldUp=new Vector3d();for(var b:bogies)worldUp.add(new Vector3d(0,1,0).rotate(b.rotation));worldUp.normalize();
            if(spread>1e-8){
                var localLine=new Vector3d(seats.get(pb)).sub(seats.get(pa));
                var worldLine=new Vector3d(bogies.get(pb).pivot).sub(bogies.get(pa).pivot);
                rotation=orientation(worldLine,worldUp).mul(orientation(localLine,new Vector3d(0,1,0)).conjugate());
            }else rotation=orientation(new Vector3d(0,0,1).rotate(bogies.get(selected).rotation),worldUp).mul(orientation(localForward,new Vector3d(0,1,0)).conjugate());
            position.zero();for(int i=0;i<seats.size();i++)position.add(new Vector3d(bogies.get(i).pivot).sub(new Vector3d(seats.get(i)).rotate(rotation)));position.div(seats.size());
            double error=0;
            for(int i=0;i<seats.size();i++){
                var delta=new Vector3d(seats.get(i)).rotate(rotation).add(position).sub(bogies.get(i).pivot);error=Math.max(error,delta.length());
                if(i!=selected)arcs[i]+=Math.clamp(delta.dot(route.at(arcs[i]).tangent),-.75,.75);
            }
            if(error<.025)break;
        }
        for(int i=0;i<seats.size();i++){
            double error=new Vector3d(seats.get(i)).rotate(rotation).add(position).distance(bogies.get(i).pivot);
            if(error>.12)throw new IllegalArgumentException("message.guidedrailways.rail.alignment");
            var carrierUp=new Vector3d(0,1,0).rotate(rotation);var bogieUp=new Vector3d(0,1,0).rotate(bogies.get(i).rotation);
            if(carrierUp.dot(bogieUp)<Math.cos(.32))throw new IllegalArgumentException("message.guidedrailways.rail.grade");
        }
        return new Car(new Vector3d(position),new Quaterniond(rotation),List.copyOf(bogies));
    }
}
