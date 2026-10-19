package com.guidedrailways;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import java.util.*;

/** Nearby matching buffer faces set the compressed spacing; buffers never become link endpoints. */
final class CouplerBufferSpacing {
    private record Bounds(Vector3d min,Vector3d max){}
    private static List<Bounds> buffers(ServerLevel level,CouplerEndpoint end,ServerSubLevel body,Vector3d origin,Vector3d axis,Vector3d x,Vector3d y){
        var result=new ArrayList<Bounds>();
        for(var pos:BlockPos.betweenClosed(end.getBlockPos().offset(-3,-1,-3),end.getBlockPos().offset(3,1,3))){
            if(!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if(!id.getNamespace().equals("railways"))continue;
            boolean buffer=id.getPath().equals("small_buffer")||id.getPath().equals("big_buffer");
            if(id.getPath().equals("headstock")||id.getPath().equals("copycat_headstock"))for(var property:state.getProperties())if(property.getName().equals("style"))buffer=state.getValue(property).toString().equalsIgnoreCase("buffer");
            if(!buffer||!state.hasProperty(CouplerBlock.FACING)||state.getValue(CouplerBlock.FACING)!=end.getBlockState().getValue(CouplerBlock.FACING)||SableCompanion.INSTANCE.getContaining(level,pos)!=body)continue;
            var shape=NativeHardwareShape.get(state,true,state.getShape(level,pos,CollisionContext.empty()));
            for(var box:shape.toAabbs()){
                var min=new Vector3d(Double.POSITIVE_INFINITY);var max=new Vector3d(Double.NEGATIVE_INFINITY);
                for(int i=0;i<8;i++){
                    var point=VehiclePhysics.world(body,new Vector3d(pos.getX()+((i&1)==0?box.minX:box.maxX),pos.getY()+((i&2)==0?box.minY:box.maxY),pos.getZ()+((i&4)==0?box.minZ:box.maxZ))).sub(origin);
                    var projected=new Vector3d(point.dot(x),point.dot(y),point.dot(axis));min.min(projected);max.max(projected);
                }result.add(new Bounds(min,max));
            }
        }return result;
    }
    static double length(ServerLevel level,CouplerEndpoint a,CouplerEndpoint b,ServerSubLevel ba,ServerSubLevel bb,double nominal){
        if(RailwayHardware.kind(a)==CouplerKind.STANDARD)return nominal;
        var pa=a.getBlockPos();var pb=b.getBlockPos();
        var wa=VehiclePhysics.world(ba,RailwayHardware.pivot(a).add(pa.getX(),pa.getY(),pa.getZ()));var wb=VehiclePhysics.world(bb,RailwayHardware.pivot(b).add(pb.getX(),pb.getY(),pb.getZ()));
        var axis=new Vector3d(wb).sub(wa);if(axis.lengthSquared()<1e-8)return nominal;axis.normalize();
        var x=new Vector3d(0,1,0).cross(axis);if(x.lengthSquared()<1e-8)return nominal;x.normalize();var y=new Vector3d(axis).cross(x);
        var boundsA=buffers(level,a,ba,wa,axis,x,y);var boundsB=buffers(level,b,bb,wb,axis,x,y);
        // A and B bounds have different axial origins but a common transverse frame.
        var transverse=new Vector3d(wb).sub(wa);double dx=transverse.dot(x),dy=transverse.dot(y);double length=nominal;
        for(var first:boundsA)for(var second:boundsB)if(first.max.x>second.min.x+dx+.01&&second.max.x+dx>first.min.x+.01&&first.max.y>second.min.y+dy+.01&&second.max.y+dy>first.min.y+.01)
            length=Math.max(length,first.max.z-second.min.z+.005);
        // A rigid link cannot reach past oversized buffers. Do not create a permanently fighting joint.
        var kind=RailwayHardware.kind(a);double allowance=kind==CouplerKind.SCREWLINK?.25:kind==CouplerKind.LINK||kind==CouplerKind.LINKLESS?1/16.0:.25;
        if(length>nominal+allowance)throw new IllegalArgumentException("message.guidedrailways.coupler.align");
        return length;
    }
    private CouplerBufferSpacing(){}
}
