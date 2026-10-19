package com.guidedrailways;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.content.trains.bogey.StandardBogeyRenderer;
public final class TrainClient {
    static void register(IEventBus bus){if(net.neoforged.fml.ModList.get().isLoaded("railways")){NativeCouplerRenderer.register(bus);SteamRailwaysClient.register();bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e)->e.enqueueWork(SteamRailwaysBogeys::verifyRegisteredStyles));}DriverClient.register();bus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e)->e.register(Content.DRIVER_MENU.get(),DriverScreen::new));bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{e.registerBlockEntityRenderer(Content.BOGIE_ENTITY.get(),Renderer::new);e.registerBlockEntityRenderer(Content.COUPLER_ENTITY.get(),CouplerRenderer::new);e.registerBlockEntityRenderer(Content.DRIVER_ENTITY.get(),DriverConsoleRenderer::new);});}
    private static final class Renderer implements BlockEntityRenderer<BogieEntity> {
        private final StandardBogeyRenderer.Small renderer=new StandardBogeyRenderer.Small();
        private final CompoundTag data=new CompoundTag();
        Renderer(BlockEntityRendererProvider.Context c){}
        @Override public void render(BogieEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            double distance=entity.distance+entity.speed*partial/20;
            pose.pushPose();pose.translate(.5,BogieGeometry.RENDER_Y,.5);
            if(BogieBlock.gauge(entity.getBlockState())==RailGauge.MONORAIL&&net.neoforged.fml.ModList.get().isLoaded("railways")){
                SteamRailwaysClient.mono(data,(float)(distance/.375*180/Math.PI),partial,pose,buffers,light,overlay);pose.popPose();return;
            }
            if(BogieBlock.variant(entity.getBlockState())!=BogeyVariant.DEFAULT&&net.neoforged.fml.ModList.get().isLoaded("railways")){
                SteamRailwaysClient.render(BogieBlock.variant(entity.getBlockState()),entity.bogeyData,(float)(distance/BogieGeometry.TREAD_RADIUS*180/Math.PI),partial,pose,buffers,light,overlay);pose.popPose();return;
            }
            pose.scale((float)BogieBlock.gauge(entity.getBlockState()).widthScale(),1,1);
            renderer.render(data,(float)(distance/BogieGeometry.TREAD_RADIUS*180/Math.PI),partial,pose,buffers,light,overlay,false);
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(BogieEntity entity){return true;}
    }
}
