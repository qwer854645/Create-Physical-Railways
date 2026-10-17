package com.guidedrailways;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
public final class BogieCollisionCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static List<double[]> mesh(ZipFile zip,String name)throws Exception{
        var vertices=new ArrayList<double[]>();var text=new String(zip.getInputStream(zip.getEntry("assets/create/models/block/track/bogey/"+name+".obj")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        for(var line:text.split("\n"))if(line.startsWith("v ")){var a=line.trim().split("\s+");vertices.add(new double[]{Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3])});}return vertices;
    }
    public static void main(String[] args)throws Exception{
        try(var zip=new ZipFile(Path.of(args[0]).toFile())){
            for(var v:mesh(zip,"bogey_frame"))check(BogieCollision.contains(.5+v[0]*.998046875,BogieGeometry.RENDER_Y+v[1]*.998046875,.5+v[2]*.998046875),"frame vertex outside collision "+java.util.Arrays.toString(v));
            for(int phase=0;phase<64;phase++){
                double a=phase*Math.PI/32,c=Math.cos(a),s=Math.sin(a);
                for(double z:new double[]{-1,1})for(var v:mesh(zip,"bogey_wheel")){
                    double y=c*v[1]-s*v[2],dz=s*v[1]+c*v[2];
                    check(BogieCollision.contains(.5+v[0],BogieGeometry.RENDER_Y+.75+y,.5+z+dz),"rotated wheel or axle outside collision "+java.util.Arrays.toString(v)+" phase="+phase);
                }
            }
        }
        double low=BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::y0).min().orElseThrow();
        check(BogieGeometry.RIDE_HEIGHT-.5+low>.125,"expanded collision must clear Create's 2/16-high track collision");
        double minZ=BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::z0).min().orElseThrow();
        double maxZ=BogieCollision.BOXES.stream().mapToDouble(BogieCollision.Box::z1).max().orElseThrow();
        check(Math.abs((maxZ-minZ)-2.973994)<1e-6,"front-rear envelope matches rotating wheel rims");
        check(BogieCollision.BOXES.size()==16,"keep collision primitives bounded without adding bodies");
        System.out.println("BogieCollisionCheck: "+checks+" assertions passed; primitives="+BogieCollision.BOXES.size());
    }
}
