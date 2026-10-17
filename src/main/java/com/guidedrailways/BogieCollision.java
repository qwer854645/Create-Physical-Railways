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
    public record Cell(int part,int x,int y,int z,List<Box> boxes){}
    public static final int CENTRE_PART=13;
    public static final List<Cell> CELLS=partition();
    private static void frame(List<Box> boxes,double x0,double y0,double z0,double x1,double y1,double z1){
        boxes.add(new Box(.5+x0*FRAME_SCALE,BogieGeometry.RENDER_Y+y0*FRAME_SCALE,.5+z0*FRAME_SCALE,.5+x1*FRAME_SCALE,BogieGeometry.RENDER_Y+y1*FRAME_SCALE,.5+z1*FRAME_SCALE));
    }
    private static List<Box> build(){
        var boxes=new ArrayList<Box>();
        frame(boxes,-0.4375000,0.7500000,-0.4375000,0.4375000,1.2500000,0.4375000);
        frame(boxes,-0.3125000,0.4375000,-0.5625000,0.3125000,1.0625000,0.5625000);
        frame(boxes,-0.2500000,1.2500000,-0.2500000,0.2500000,1.5000000,0.2500000);
        frame(boxes,0.2241120,0.5000000,0.1250000,0.5776650,0.8535530,0.3125000);
        frame(boxes,-0.5776650,0.5000000,0.1250000,-0.2241120,0.8535530,0.3125000);
        frame(boxes,0.2241120,0.5000000,-0.3125000,0.5776650,0.8535530,-0.1250000);
        frame(boxes,-0.5776650,0.5000000,-0.3125000,-0.2241120,0.8535530,-0.1250000);
        frame(boxes,0.3125000,0.6875000,-0.5000000,0.9375000,1.3125000,0.5000000);
        frame(boxes,-0.9375000,0.6875000,-0.5000000,-0.3125000,1.3125000,0.5000000);
        frame(boxes,0.3750000,0.3964470,-1.3535530,0.7500000,1.1035530,-0.6464470);
        frame(boxes,-0.7500000,0.3964470,-1.3535530,-0.3750000,1.1035530,-0.6464470);
        frame(boxes,0.3750000,0.3964470,0.6464460,0.7500000,1.1035530,1.3535530);
        frame(boxes,-0.7500000,0.3964470,0.6464460,-0.3750000,1.1035530,1.3535530);
        frame(boxes,0.4375000,0.8383880,-0.9116120,0.6875000,1.4571070,-0.2928930);
        frame(boxes,-0.6875000,0.8383880,-0.9116120,-0.4375000,1.4571070,-0.2928930);
        frame(boxes,0.4375000,0.8383880,0.2928930,0.6875000,1.4571070,0.9116120);
        frame(boxes,-0.6875000,0.8383880,0.2928930,-0.4375000,1.4571070,0.9116120);
        frame(boxes,-0.3125000,0.4375000,-1.3125000,0.3125000,1.0625000,-0.6875000);
        frame(boxes,-0.0937500,1.0625000,-1.0937500,0.0937500,1.2500000,-0.9062500);
        frame(boxes,-0.3125000,0.4375000,0.6875000,0.3125000,1.0625000,1.3125000);
        frame(boxes,-0.0937500,1.0625000,0.9062500,0.0937500,1.2500000,1.0937500);
        // Four tyre/flange envelopes plus two narrow axle caps; all in the existing frame body.
        double axleY=BogieGeometry.RENDER_Y+BogieGeometry.AXLE_RENDER_Y,r=RIM_RADIUS;
        for(double z:new double[]{-1,1}){
            for(double side:new double[]{-1,1})boxes.add(new Box(.5+(side<0?-1.0625:.75),axleY-r,.5+z-r,.5+(side<0?-.75:1.0625),axleY+r,.5+z+r));
            boxes.add(new Box(.5-1.1875,axleY-.176777,.5+z-.176777,.5+1.1875,axleY+.176777,.5+z+.176777));
        }
        return List.copyOf(boxes);
    }
    private static List<Cell> partition(){
        var cells=new ArrayList<Cell>();
        for(int y=-1;y<=0;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var local=new ArrayList<Box>();
            for(var b:BOXES){
                double x0=Math.max(x,b.x0)-x,y0=Math.max(y,b.y0)-y,z0=Math.max(z,b.z0)-z;
                double x1=Math.min(x+1,b.x1)-x,y1=Math.min(y+1,b.y1)-y,z1=Math.min(z+1,b.z1)-z;
                if(x1-x0>1e-9&&y1-y0>1e-9&&z1-z0>1e-9)local.add(new Box(x0,y0,z0,x1,y1,z1));
            }
            if(!local.isEmpty())cells.add(new Cell((y+1)*9+(x+1)*3+z+1,x,y,z,List.copyOf(local)));
        }
        return List.copyOf(cells);
    }
    public static boolean containsAfterCellClipping(double x,double y,double z){
        return CELLS.stream().anyMatch(c->c.boxes.stream().anyMatch(b->b.contains(x-c.x,y-c.y,z-c.z)));
    }
    public static boolean contains(double x,double y,double z){return BOXES.stream().anyMatch(b->b.contains(x,y,z));}
}
