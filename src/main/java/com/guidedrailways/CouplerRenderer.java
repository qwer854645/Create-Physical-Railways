package com.guidedrailways;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.companion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Quaternionf;
/** Moving hardware is visual only; the native coupling joint remains authoritative. */
public final class CouplerRenderer implements BlockEntityRenderer<CouplerEntity> {
    CouplerRenderer(BlockEntityRendererProvider.Context context){}
    private static ClientSubLevelAccess body(CouplerEndpoint e){
        var b=SableCompanion.INSTANCE.getContaining(e.getLevel(),e.getBlockPos());
        return b instanceof ClientSubLevelAccess client?client:null;
    }
    private static Vector3d worldAnchor(CouplerEndpoint e,float partial){
        var p=new Vector3d(e.anchor).add(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ());
        var body=body(e);return body==null?p:body.renderPose(partial).transformPosition(p,new Vector3d());
    }
    private static Vector3d worldUp(CouplerEndpoint e,float partial){
        var body=body(e);return body==null?new Vector3d(0,1,0):body.renderPose(partial).transformNormal(new Vector3d(0,1,0),new Vector3d());
    }
    @Override public void render(CouplerEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var e=entity.endpoint;
        var facing=e.getBlockState().getValue(CouplerBlock.FACING);
        var pivot=CouplerVisual.pivot(facing.getStepX(),facing.getStepZ());
        var tip=CouplerVisual.restTip(facing.getStepX(),facing.getStepZ());var up=new Vector3d(0,1,0);
        if(e.visualConnected&&e.hasGeometry&&body(e)!=null){
            var target=worldAnchor(e,partial);var worldUp=worldUp(e,partial);
            var peer=e.visualPeerPos!=null&&e.getLevel().hasChunkAt(e.visualPeerPos)?CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos)):null;
            if(peer!=null
                    &&peer.endpointId.equals(e.visualPeerId)&&peer.hasGeometry&&body(peer)!=null){
                target=CouplerVisual.joint(target,worldAnchor(peer,partial));worldUp.add(worldUp(peer,partial));
            }
            var own=body(e);
            if(own!=null){var rendered=own.renderPose(partial);target=rendered.transformPositionInverse(target,new Vector3d());up=rendered.transformNormalInverse(worldUp,new Vector3d());}
            else up=worldUp;
            tip=target.sub(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ());
        }
        // Unloaded/replaced sublevels must not turn a plot coordinate into a huge drawbar.
        if(!tip.isFinite()||tip.distanceSquared(pivot)>16){tip=CouplerVisual.restTip(facing.getStepX(),facing.getStepZ());up.set(0,1,0);}
        var layout=CouplerVisual.layout(pivot,tip,up,net.minecraft.util.Mth.lerp(partial,e.previousLockProgress,e.lockProgress),.73);
        pose.pushPose();pose.translate(pivot.x,pivot.y,pivot.z);pose.mulPose(new Quaternionf(layout.rotation()));
        var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        var consumer=buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        for(var box:layout.boxes()){
            String name=switch(box.material()){case IRON->"industrial_iron_block";case DARK->"dark_metal_block";case BRASS->"brass_block";};
            var sprite=atlas.apply(ResourceLocation.fromNamespaceAndPath("create","block/"+name));
            // Tile long drawbars in one-block sections, keeping atlas UVs inside their sprite.
            for(double z=box.z0();z<box.z1()-1e-8;z+=1)box(pose.last(),consumer,sprite,box.x0(),box.y0(),z,box.x1(),box.y1(),Math.min(z+1,box.z1()),light,overlay);
        }
        pose.popPose();
    }
    static void box(PoseStack.Pose p,VertexConsumer v,TextureAtlasSprite s,double x,double y,double z,double X,double Y,double Z,int light,int overlay){
        face(p,v,s,new double[][]{{x,y,z},{x,Y,z},{X,Y,z},{X,y,z}},0,0,-1,X-x,Y-y,light,overlay);
        face(p,v,s,new double[][]{{X,y,Z},{X,Y,Z},{x,Y,Z},{x,y,Z}},0,0,1,X-x,Y-y,light,overlay);
        face(p,v,s,new double[][]{{x,y,Z},{x,Y,Z},{x,Y,z},{x,y,z}},-1,0,0,Z-z,Y-y,light,overlay);
        face(p,v,s,new double[][]{{X,y,z},{X,Y,z},{X,Y,Z},{X,y,Z}},1,0,0,Z-z,Y-y,light,overlay);
        face(p,v,s,new double[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}},0,1,0,X-x,Z-z,light,overlay);
        face(p,v,s,new double[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}},0,-1,0,X-x,Z-z,light,overlay);
    }
    private static void face(PoseStack.Pose p,VertexConsumer v,TextureAtlasSprite s,double[][] points,float nx,float ny,float nz,double width,double height,int light,int overlay){
        float[][] uv={{0,(float)height},{0,0},{(float)width,0},{(float)width,(float)height}};
        for(int i=0;i<4;i++)v.addVertex(p,(float)points[i][0],(float)points[i][1],(float)points[i][2]).setColor(255,255,255,255)
            .setUv(s.getU(uv[i][0]),s.getV(uv[i][1])).setOverlay(overlay).setLight(light).setNormal(p,nx,ny,nz);
    }
    @Override public boolean shouldRenderOffScreen(CouplerEntity e){return true;}
}

