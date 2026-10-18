package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
/** A real settings screen, never created by ordinary takeover. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private Button forward,neutral,reverse,autoStart,autoPause;
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=300;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private Button direction(String key,int x,int id){return addRenderableWidget(Button.builder(text(key),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+96,86,20).build());}
    private Button automatic(String key,int x,int id){return addRenderableWidget(Button.builder(Component.translatable("gui.guidedrailways.auto."+key),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+155,86,20).build());}
    @Override protected void init(){
        super.init();forward=direction("forward",12,3);neutral=direction("neutral",107,4);reverse=direction("reverse",202,5);
        autoStart=automatic("start",12,30);autoPause=automatic("pause",107,31);
        addRenderableWidget(Button.builder(text("close_settings"),b->onClose()).bounds(leftPos+12,topPos+185,276,20).build());
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+302,topPos+302,0xffa48a50);g.fill(leftPos,topPos,leftPos+300,topPos+300,0xff252d32);
        slotBackground(g,268,133);
        for(var slot:menu.slots)if(slot.index!=0)slotBackground(g,slot.x,slot.y);
        g.drawString(font,Component.translatable("item.create.schedule"),leftPos+215,topPos+123,0xffb9c5cc,false);
        g.drawString(font,playerInventoryTitle,leftPos+69,topPos+206,0xffb9c5cc,false);
        g.drawString(font,title,leftPos+12,topPos+12,0xffe6cc8c,false);
        g.drawString(font,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),leftPos+12,topPos+35,0xff8ae2cd,false);
        g.drawString(font,text("power",String.format(java.util.Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),leftPos+12,topPos+53,0xffb9c5cc,false);
        g.drawString(font,text("settings_only"),leftPos+12,topPos+76,0xffb9c5cc,false);
        g.drawString(font,Component.translatable("gui.guidedrailways.auto.state."+menu.value(13)),leftPos+12,topPos+123,0xffe6cc8c,false);
        if(menu.value(12)!=0)g.drawString(font,Component.translatable("gui.guidedrailways.auto.progress",menu.value(14),menu.value(15)),leftPos+12,topPos+138,0xffb9c5cc,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    private void slotBackground(GuiGraphics g,int x,int y){g.fill(leftPos+x-1,topPos+y-1,leftPos+x+17,topPos+y+17,0xffa48a50);g.fill(leftPos+x,topPos+y,leftPos+x+16,topPos+y+16,0xff151b20);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){forward.active=neutral.active=reverse.active=menu.value(11)!=0;autoStart.active=autoPause.active=menu.value(12)!=0&&menu.value(16)!=0;super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){DriverClient.exit();onClose();return true;}return super.keyPressed(key,scan,mods);}
}
