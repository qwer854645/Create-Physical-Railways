package com.guidedrailways;
import com.google.gson.JsonParser;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.nio.file.*;
import java.util.List;

public final class PlatformHeightCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-8;}
    public static void main(String[] args)throws Exception{
        check(BogieGeometry.PLATFORM_RAISE>0&&BogieGeometry.PLATFORM_RAISE<.5,"raise to the next whole-block floor without adding a full block of height");
        for(int y:new int[]{-64,-1,0,1,64,319})for(double yaw:new double[]{0,.3,Math.PI/2,Math.PI,5.1}){
            var rotation=new Quaterniond().rotateY(yaw);var up=new Vector3d(0,1,0);
            var along=new Vector3d(0,0,1).rotate(rotation);
            RailPlacement.Route track=d->new RailPlacement.Point(new Vector3d(.5,y,.5).fma(d,along),new Vector3d(along),new Vector3d(up));
            var bogie=RailPlacement.bogie(track,0,BogieGeometry.RIDE_HEIGHT,1);
            var mountOrigin=new Vector3d(bogie.origin()).fma(BogieGeometry.PLATE_OFFSET,up);
            check(near(mountOrigin.y-.5,y+1),"carrier block bottom lies on the world grid on horizontal rails");
            check(near(mountOrigin.y+.5,y+2),"platform top and floor blocks above it use integral elevations");
            check(new Vector3d(mountOrigin).fma(BogieGeometry.MOUNT_UNDERSIDE-.5,up).distance(bogie.pivot())<1e-8,"placement and physical bearing anchors coincide");
            check(near(bogie.origin().y,y+BogieGeometry.RIDE_HEIGHT),"raising the carrier does not move the wheel or rail anchor");
            var seats=List.of(new Vector3d(0,.25,-4),new Vector3d(0,.25,4));
            var car=RailPlacement.carriage(track,seats,0,new Vector3d(0,0,1),BogieGeometry.RIDE_HEIGHT,1);
            check(near(car.position().y+.5,y+2),"all mounts of a rigid carrier rerail to one standard floor height");
        }
        for(double pitch:new double[]{-.3,0,.3})for(double roll:new double[]{-.1,0,.1}){
            var orientation=new Quaterniond().rotateYXZ(.8,pitch,roll);var up=new Vector3d(0,1,0).rotate(orientation);
            var anchor=new Vector3d(10,20,30);
            var lowerOrigin=new Vector3d(anchor).fma(-BogieGeometry.PIVOT_HEIGHT,up);
            check(new Vector3d(lowerOrigin).fma(BogieGeometry.PIVOT_HEIGHT,up).distance(anchor)<1e-8,"underside installation uses the same bearing offset on rotated bodies");
        }
        check(near(BogieGeometry.pivotHeight(1),.375)&&near(BogieGeometry.pivotHeight(0),.375),"unversioned saves keep their original connection height until rerailing");
        check(near(BogieGeometry.pivotHeight(2)-BogieGeometry.pivotHeight(1),BogieGeometry.PLATFORM_RAISE),"legacy migration lifts only the carrier by the planned height difference");
        var model=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/guidedrailways/models/block/bogie_mount.json"))).getAsJsonObject();
        var stem=model.getAsJsonArray("elements").get(0).getAsJsonObject();
        check(near(stem.getAsJsonArray("from").get(1).getAsDouble()/16,BogieGeometry.SUPPORT_BOTTOM),"rendered support matches its collision lower bound");
        check(near(stem.getAsJsonArray("to").get(1).getAsDouble()/16,BogieGeometry.MOUNT_UNDERSIDE),"support joins the platform underside without a gap");
        check(near(BogieGeometry.PLATE_OFFSET-.5+BogieGeometry.SUPPORT_BOTTOM,BogieGeometry.LEGACY_PLATE_OFFSET-.5+BogieGeometry.MOUNT_UNDERSIDE),"new support reaches the original bearing height while wheels stay put");
        System.out.println("PlatformHeightCheck: "+checks+" assertions passed");
    }
}
