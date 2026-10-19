package com.guidedrailways;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/** Uses the installed mod's animated model and textures, without copying its assets. */
public final class SteamRailwaysClient {
    private static net.minecraft.network.chat.Component selectedName;
    public static void selection(com.simibubi.create.content.trains.bogey.BogeyStyle style){selectedName=style.displayName.copy();}
    static void register(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e)->selectedName=null);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.Key e)->{
            if(e.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS&&com.railwayteam.railways.registry.CRKeys.BOGEY_MENU.getKeybind().matches(e.getKey(),e.getScanCode()))open();
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post e)->{
            if(e.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS&&com.railwayteam.railways.registry.CRKeys.BOGEY_MENU.getKeybind().matchesMouse(e.getButton()))open();
        });
        PhysicalBogeySelection.tooltip=lines->{
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.guidedrailways.bogey.menu",com.railwayteam.railways.registry.CRKeys.BOGEY_MENU.getKeybind().getTranslatedKeyMessage()).withStyle(net.minecraft.ChatFormatting.GRAY));
            if(selectedName!=null)lines.add(net.minecraft.network.chat.Component.translatable("tooltip.guidedrailways.bogey.selected",selectedName).withStyle(net.minecraft.ChatFormatting.GRAY));
        };
    }
    private static void open(){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.screen!=null||mc.player==null||mc.player.isSpectator()||!(mc.player.getMainHandItem().getItem() instanceof BogieItem))return;
        net.createmod.catnip.gui.ScreenOpener.open(new com.railwayteam.railways.content.bogey_menu.BogeyMenuScreen());
    }
    static void render(BogeyVariant variant,CompoundTag data,float angle,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var style=switch(variant){case FREIGHT->CRBogeyStyles.FREIGHT;case PASSENGER->CRBogeyStyles.PASSENGER;case MODERN->CRBogeyStyles.MODERN;case BLOMBERG->CRBogeyStyles.BLOMBERG;case Y25->CRBogeyStyles.Y25;default->com.simibubi.create.AllBogeyStyles.STANDARD;};
        style.render(BogeySizes.SMALL,partial,pose,buffers,light,overlay,angle,data,false);
    }
    static void mono(CompoundTag data,float angle,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        CRBogeyStyles.MONOBOGEY.render(BogeySizes.SMALL,partial,pose,buffers,light,overlay,angle,data,false);
    }
}
