package com.guidedrailways;
import java.util.ArrayList;
import java.util.List;
/** Coarse compound collision fitted to Create's standard small bogie, not triangle-mesh contact. */
public final class BogieCollision {
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1){
        public boolean contains(double x,double y,double z){return x>=x0-1e-6&&x<=x1+1e-6&&y>=y0-1e-6&&y<=y1+1e-6&&z>=z0-1e-6&&z<=z1+1e-6;}
    }
    private static final double FRAME_SCALE=.998046875;
    public static final double RIM_RADIUS=.486997;
    public static final List<Box> BOXES=build();
    private static void frame(List<Box> boxes,double x0,double y0,double z0,double x1,double y1,double z1){
        boxes.add(new Box(.5+x0*FRAME_SCALE,BogieGeometry.RENDER_Y+y0*FRAME_SCALE,.5+z0*FRAME_SCALE,.5+x1*FRAME_SCALE,BogieGeometry.RENDER_Y+y1*FRAME_SCALE,.5+z1*FRAME_SCALE));
    }
    private static List<Box> build(){
        var boxes=new ArrayList<Box>();
        frame(boxes,-.577665,.4375,-.5625,.577665,1.25,.5625);
        frame(boxes,-.25,1.25,-.25,.25,1.5,.25);
        for(double side:new double[]{-1,1}){
            frame(boxes,side<0?-.9375:.3125,.6875,-.5,side<0?-.3125:.9375,1.3125,.5);
            frame(boxes,-.75,.396447,side-.353554,.75,1.103553,side+.353554);
            frame(boxes,-.09375,1.0625,side-.09375,.09375,1.25,side+.09375);
            frame(boxes,side<0?-.6875:.4375,.838388,-.911612,side<0?-.4375:.6875,1.457107,.911612);
        }
        // Four tyre/flange envelopes plus two narrow axle caps; all in the existing frame body.
        double axleY=BogieGeometry.RENDER_Y+BogieGeometry.AXLE_RENDER_Y,r=RIM_RADIUS;
        for(double z:new double[]{-1,1}){
            for(double side:new double[]{-1,1})boxes.add(new Box(.5+(side<0?-1.0625:.75),axleY-r,.5+z-r,.5+(side<0?-.75:1.0625),axleY+r,.5+z+r));
            boxes.add(new Box(.5-1.1875,axleY-.176777,.5+z-.176777,.5+1.1875,axleY+.176777,.5+z+.176777));
        }
        return List.copyOf(boxes);
    }
    public static boolean contains(double x,double y,double z){return BOXES.stream().anyMatch(b->b.contains(x,y,z));}
}
