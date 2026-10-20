package com.guidedrailways;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/** Uses the installed mod's animated model and textures, without copying its assets. */
public final class SteamRailwaysClient {
    private static net.minecraft.network.chat.Component selectedName;
    private static final java.util.Map<net.minecraft.resources.ResourceLocation,BogeySizes.BogeySize> preferredSizes=new java.util.HashMap<>();
    public static BogeySizes.BogeySize size(com.simibubi.create.content.trains.bogey.BogeyStyle style,BogeySizes.BogeySize fallback){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof BogieItem))return fallback;
        var result=preferredSizes.getOrDefault(style.id,fallback);return result!=null&&style.validSizes().contains(result)?result:fallback;
    }
    private static net.minecraft.network.chat.Component sizeName(BogeySizes.BogeySize size){return net.minecraft.network.chat.Component.translatable(size==BogeySizes.LARGE?"gui.guidedrailways.bogey.large":"gui.guidedrailways.bogey.small");}
    public static net.minecraft.client.gui.components.Button sizeButton(int x,int y,java.util.function.Supplier<com.simibubi.create.content.trains.bogey.BogeyStyle> selected){
        var button=new CreateActionButton(x,y,104,net.minecraft.network.chat.Component.empty(),b->{
            var style=selected.get();if(style==null)return;var sizes=style.validSizes().stream().sorted(java.util.Comparator.comparingDouble(BogeySizes.BogeySize::wheelRadius)).toList();if(sizes.isEmpty())return;
            var current=size(style,com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerClient.getSize(style));preferredSizes.put(style.id,sizes.get((Math.max(0,sizes.indexOf(current))+1)%sizes.size()));refreshSize(b,style);
        });refreshSize(button,selected.get());return button;
    }
    public static void refreshSize(net.minecraft.client.gui.components.Button button,com.simibubi.create.content.trains.bogey.BogeyStyle style){
        button.active=style!=null&&style.validSizes().size()>1;
        var value=style==null?BogeySizes.SMALL:size(style,com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerClient.getSize(style));
        button.setMessage(net.minecraft.network.chat.Component.translatable("gui.guidedrailways.bogey.size",sizeName(value)));
    }
    public static void selection(com.simibubi.create.content.trains.bogey.BogeyStyle style){selectedName=style.displayName.copy();}
    static void register(){
        NativeBogeyCapture.register();
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
        var style=com.simibubi.create.AllBogeyStyles.BOGEY_STYLES.get(net.minecraft.resources.ResourceLocation.parse(variant.id));
        var size=com.simibubi.create.content.trains.bogey.BogeySizes.all().get(net.minecraft.resources.ResourceLocation.parse(variant.size));
        if(style!=null&&size!=null)style.render(size,partial,pose,buffers,light,overlay,angle,data,false);
    }

    static void mono(CompoundTag data,float angle,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.translate(0,1.5078125,0);
        CRBogeyStyles.MONOBOGEY.render(BogeySizes.SMALL,partial,pose,buffers,light,overlay,angle,data,false);
    }
}
