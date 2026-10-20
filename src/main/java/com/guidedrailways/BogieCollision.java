package com.guidedrailways;
import java.util.ArrayList;
import java.util.List;
/** Cached compound collision fitted to frame components and circular wheel envelopes. */
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
    private static final java.util.Map<BogeyVariant,List<Box>> NATIVE_BOXES=new java.util.HashMap<>();
    private static final java.util.Map<BogeyVariant,List<Cell>> NATIVE_CELLS=new java.util.HashMap<>();
    static {
        for(var variant:BogeyVariant.values())if(variant!=BogeyVariant.DEFAULT){
            var boxes=variant.boxes.stream().map(b->new Box(b[0],b[1],b[2],b[3],b[4],b[5])).toList();
            NATIVE_BOXES.put(variant,boxes);NATIVE_CELLS.put(variant,partition(boxes));
        }
    }
    public static List<Box> boxes(BogeyVariant variant){return variant==BogeyVariant.DEFAULT?BOXES:NATIVE_BOXES.get(variant);}
    public static List<Cell> cells(BogeyVariant variant){return variant==BogeyVariant.DEFAULT?CELLS:NATIVE_CELLS.get(variant);}
    public static List<Box> boxes(RailGauge gauge){return boxes(gauge==RailGauge.STANDARD?BogeyVariant.DEFAULT:BogeyVariant.defaultFor(gauge));}
    public static List<Cell> cells(RailGauge gauge){return cells(gauge==RailGauge.STANDARD?BogeyVariant.DEFAULT:BogeyVariant.defaultFor(gauge));}
    public record Shape(int id,BogeyVariant variant,Cell cell,double mass){}
    private static final List<Shape> SHAPES=new ArrayList<>();
    private static final java.util.Map<BogeyVariant,java.util.Map<Integer,Integer>> SHAPE_IDS=new java.util.HashMap<>();
    static{
        for(var variant:BogeyVariant.values()){
            var cells=cells(variant);var ids=new java.util.HashMap<Integer,Integer>();
            for(var cell:cells)if(cell.part()!=CENTRE_PART){
                double mass=variant==BogeyVariant.DEFAULT?(cell.part()==9||cell.part()==11||cell.part()==15||cell.part()==17?8:0):32.0/(cells.size()-1);
                int id=SHAPES.size();ids.put(cell.part(),id);SHAPES.add(new Shape(id,variant,cell,mass));
            }
            SHAPE_IDS.put(variant,java.util.Map.copyOf(ids));
        }
    }
    public static int shapeCount(){return SHAPES.size();}
    public static Shape shape(int id){return SHAPES.get(id);}
    public static int shapeId(BogeyVariant variant,int part){return SHAPE_IDS.get(variant).get(part);}
    private static List<Cell> partition(List<Box> boxes){
        var result=new ArrayList<Cell>();int part=0;
        int minX=(int)Math.floor(boxes.stream().mapToDouble(Box::x0).min().orElse(0)),maxX=(int)Math.ceil(boxes.stream().mapToDouble(Box::x1).max().orElse(1));
        int minY=(int)Math.floor(boxes.stream().mapToDouble(Box::y0).min().orElse(0)),maxY=(int)Math.ceil(boxes.stream().mapToDouble(Box::y1).max().orElse(1));
        int minZ=(int)Math.floor(boxes.stream().mapToDouble(Box::z0).min().orElse(0)),maxZ=(int)Math.ceil(boxes.stream().mapToDouble(Box::z1).max().orElse(1));
        for(int y=minY;y<maxY;y++)for(int x=minX;x<maxX;x++)for(int z=minZ;z<maxZ;z++){
            var local=new ArrayList<Box>();for(var b:boxes){
                double x0=Math.max(x,b.x0)-x,y0=Math.max(y,b.y0)-y,z0=Math.max(z,b.z0)-z;
                double x1=Math.min(x+1,b.x1)-x,y1=Math.min(y+1,b.y1)-y,z1=Math.min(z+1,b.z1)-z;
                if(x1-x0>1e-9&&y1-y0>1e-9&&z1-z0>1e-9)local.add(new Box(x0,y0,z0,x1,y1,z1));
            }
            if(local.isEmpty())continue;while(part==CENTRE_PART)part++;
            int id=x==0&&y==0&&z==0?CENTRE_PART:part++;
            if(id>127)throw new IllegalStateException("Bogie collision profile exceeds part capacity");
            result.add(new Cell(id,x,y,z,List.copyOf(local)));
        }
        return List.copyOf(result);
    }
    /** Fixed mass centre of the current built-in bogey frame. */
    public static double massCenterY(){
        var cell=CELLS.stream().filter(c->c.part==CENTRE_PART).findFirst().orElseThrow();
        double lo=cell.boxes.stream().mapToDouble(Box::y0).min().orElseThrow();
        double hi=cell.boxes.stream().mapToDouble(Box::y1).max().orElseThrow();
        return (lo+hi)*.5;
    }
    private static void frame(List<Box> boxes,double x0,double y0,double z0,double x1,double y1,double z1){
        boxes.add(new Box(.5+x0*FRAME_SCALE,BogieGeometry.RENDER_Y+y0*FRAME_SCALE,.5+z0*FRAME_SCALE,.5+x1*FRAME_SCALE,BogieGeometry.RENDER_Y+y1*FRAME_SCALE,.5+z1*FRAME_SCALE));
    }
    private static List<Box> build(){
        var boxes=new ArrayList<Box>();
        frame(boxes,-0.4375000,0.7500000,-0.4375000,0.4375000,1.2500000,0.4375000);
        frame(boxes,-0.3125000,0.4375000,-0.5625000,0.3125000,1.0625000,0.5625000);
        frame(boxes,-0.2500000,1.2500000,-0.2500000,0.2500000,1.5000000,0.2500000);
        for(double side:new double[]{-1,1})for(double z:new double[]{-.3125,.125})
            prism(boxes,false,z,z+.1875,new double[][]{{side*.224112,.588388},{side*.3125,.5},{side*.577665,.765165},{side*.489277,.853553}});
        frame(boxes,0.3125000,0.6875000,-0.5000000,0.9375000,1.3125000,0.5000000);
        frame(boxes,-0.9375000,0.6875000,-0.5000000,-0.3125000,1.3125000,0.5000000);
        for(double side:new double[]{-1,1})for(double end:new double[]{-1,1}){
            prism(boxes,true,side<0?-.75:.375,side<0?-.375:.75,new double[][]{{.396447,end},{.75,end-.353553},{1.103553,end},{.75,end+.353553}});
            prism(boxes,true,side<0?-.6875:.4375,side<0?-.4375:.6875,new double[][]{{.838388,end*.734835},{1.015165,end*.911612},{1.457107,end*.469670},{1.280330,end*.292893}});
        }
        frame(boxes,-0.3125000,0.4375000,-1.3125000,0.3125000,1.0625000,-0.6875000);
        frame(boxes,-0.0937500,1.0625000,-1.0937500,0.0937500,1.2500000,-0.9062500);
        frame(boxes,-0.3125000,0.4375000,0.6875000,0.3125000,1.0625000,1.3125000);
        frame(boxes,-0.0937500,1.0625000,0.9062500,0.0937500,1.2500000,1.0937500);
        // Static swept circles: separate tread, flange and shaft widths. No per-frame rebaking.
        double axleY=BogieGeometry.RENDER_Y+BogieGeometry.AXLE_RENDER_Y;
        for(double z:new double[]{-1,1})for(double side:new double[]{-1,1}){
            cylinder(boxes,side<0?-1.0625:.875,side<0?-.875:1.0625,axleY,z,.419347);
            cylinder(boxes,side<0?-.875:.75,side<0?-.75:.875,axleY,z,RIM_RADIUS);
            cylinder(boxes,side<0?-1.1875:.1875,side<0?-.1875:1.1875,axleY,z,.176777);
        }
        // A continuous narrow belt covers the re-entrant gaps between wheel flanges.
        // It stays at axle height: the circular bottom clearance and detailed frame survive.
        double x=1.1875+1.0/64,z=1+RIM_RADIUS+1.0/128,t=1.0/32;
        for(double side:new double[]{-1,1}){
            double x0=side<0?-x:x-t,z0=side<0?-z:z-t;
            boxes.add(new Box(.5+x0,axleY-.125,.5-z,.5+x0+t,axleY+.125,.5+z));
            boxes.add(new Box(.5-x,axleY-.125,.5+z0,.5+x,axleY+.125,.5+z0+t));
        }
        return List.copyOf(boxes);
    }
    /** Horizontal strips circumscribe each circle with less than one pixel radial excess. */
    private static void cylinder(List<Box> boxes,double x0,double x1,double cy,double cz,double radius){
        for(int i=0;i<12;i++){
            double a=-Math.PI/2+i*Math.PI/12,b=-Math.PI/2+(i+1)*Math.PI/12;
            double near=a<=0&&b>=0?0:Math.min(Math.abs(a),Math.abs(b));
            double width=radius*Math.cos(near);
            boxes.add(new Box(.5+x0,cy+radius*Math.sin(a),.5+cz-width,.5+x1,cy+radius*Math.sin(b),.5+cz+width));
        }
    }
    /** Slice a convex frame cross-section; yz=true extrudes along x, otherwise along z. */
    private static void prism(List<Box> boxes,boolean yz,double start,double end,double[][] polygon){
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var p:polygon){double h=p[yz?0:1];min=Math.min(min,h);max=Math.max(max,h);}
        for(int i=0;i<12;i++){
            double lo=min+(max-min)*i/12,hi=min+(max-min)*(i+1)/12;
            double left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY;
            for(int j=0;j<polygon.length;j++){
                var p=polygon[j];var q=polygon[(j+1)%polygon.length];
                double ph=p[yz?0:1],qh=q[yz?0:1],pw=p[yz?1:0],qw=q[yz?1:0];
                if(ph>=lo-1e-9&&ph<=hi+1e-9){left=Math.min(left,pw);right=Math.max(right,pw);}
                if(Math.abs(qh-ph)>1e-9)for(double h:new double[]{lo,hi}){
                    double t=(h-ph)/(qh-ph);
                    if(t>=-1e-9&&t<=1+1e-9){double w=pw+(qw-pw)*Math.clamp(t,0,1);left=Math.min(left,w);right=Math.max(right,w);}
                }
            }
            if(yz)frame(boxes,start,lo,left,end,hi,right);else frame(boxes,left,lo,start,right,hi,end);
        }
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
