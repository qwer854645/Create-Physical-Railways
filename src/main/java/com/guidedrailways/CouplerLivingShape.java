package com.guidedrailways;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.phys.shapes.*;
import org.joml.Vector3d;
/** Entity movement, including Sable's context-free queries; native colliders use the fixed buffer. */
final class CouplerLivingShape {
    private static SubLevelAccess body(CouplerEndpoint e){return SableCompanion.INSTANCE.getContaining(e.getLevel(),e.getBlockPos());}
    private static Vector3d anchor(CouplerEndpoint e,SubLevelAccess body){return body.logicalPose().transformPosition(new Vector3d(e.anchor).add(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ()),new Vector3d());}
    static VoxelShape get(CouplerEndpoint e){
        long tick=e.getLevel().getGameTime();if(e.livingShape!=null&&e.livingShapeTick==tick)return e.livingShape;
        var facing=e.getBlockState().getValue(CouplerBlock.FACING);var pivot=CouplerVisual.pivot(facing.getStepX(),facing.getStepZ());var tip=CouplerVisual.restTip(facing.getStepX(),facing.getStepZ());var up=new Vector3d(0,1,0);
        var own=body(e);
        if(e.visualConnected&&e.hasGeometry&&own!=null){
            var target=anchor(e,own);var worldUp=own.logicalPose().transformNormal(new Vector3d(0,1,0),new Vector3d());
            var peer=e.visualPeerPos!=null&&e.getLevel().hasChunkAt(e.visualPeerPos)?CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos)):null;
            if(peer!=null&&peer.endpointId.equals(e.visualPeerId)&&peer.hasGeometry){
                var other=body(peer);if(other!=null){target=CouplerVisual.joint(target,anchor(peer,other));worldUp.add(other.logicalPose().transformNormal(new Vector3d(0,1,0),new Vector3d()));}
            }
            tip=own.logicalPose().transformPositionInverse(target,new Vector3d()).sub(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ());
            up=own.logicalPose().transformNormalInverse(worldUp,new Vector3d());
        }
        if(!tip.isFinite()||tip.distanceSquared(pivot)>16){tip=CouplerVisual.restTip(facing.getStepX(),facing.getStepZ());up.set(0,1,0);}
        var layout=CouplerVisual.layout(pivot,tip,up,e.visualConnected?1:0,.73);var shape=Shapes.empty();
        for(var b:CouplerLivingGeometry.boxes(layout))shape=Shapes.or(shape,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
        e.livingShapeTick=tick;e.livingShape=shape;return shape;
    }
}
