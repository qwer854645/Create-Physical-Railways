package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
/** A real settings screen, never created by ordinary takeover. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private Button forward,neutral,reverse,autoStart,autoPause,autoSkip,autoRestart,takeover,autoRemove;
    private Button saveSpeed;private EditBox speed;private int syncedSpeed=-1;private boolean speedEdited;
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=270;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private Button direction(String key,int x,int id){return addRenderableWidget(Button.builder(text(key),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+96,86,20).build());}
    private Button automatic(String key,int x,int id){return addRenderableWidget(Button.builder(Component.translatable("gui.guidedrailways.auto."+key),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+155,86,20).build());}
    @Override protected void init(){
        super.init();forward=direction("forward",12,3);neutral=direction("neutral",107,4);reverse=direction("reverse",202,5);
        autoStart=automatic("start",12,30);autoPause=automatic("pause",107,31);autoSkip=automatic("skip",202,33);
        autoRestart=addRenderableWidget(Button.builder(Component.translatable("gui.guidedrailways.auto.restart"),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,34);}).bounds(leftPos+12,topPos+185,86,20).build());
        takeover=addRenderableWidget(Button.builder(text("takeover"),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,35);}).bounds(leftPos+107,topPos+185,86,20).build());
        autoRemove=addRenderableWidget(Button.builder(Component.translatable("gui.guidedrailways.auto.remove"),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,32);}).bounds(leftPos+202,topPos+185,86,20).build());
        String previous=speed==null?"":speed.getValue();
        speed=new EditBox(font,leftPos+178,topPos+212,58,18,text("speed_at_256"));speed.setMaxLength(5);
        speed.setFilter(value->value.matches("[0-9]{0,3}(\\.[0-9]?)?"));speed.setValue(previous);
        speed.setResponder(value->speedEdited=true);addRenderableWidget(speed);
        saveSpeed=addRenderableWidget(Button.builder(text("apply_speed"),b->applySpeed()).bounds(leftPos+242,topPos+211,46,20).build());
        syncedSpeed=-1;
        addRenderableWidget(Button.builder(text("close_settings"),b->onClose()).bounds(leftPos+12,topPos+241,276,20).build());
    }
    private int enteredSpeed(){try{double value=Double.parseDouble(speed.getValue());int tenths=(int)Math.round(value*10);return SpeedSetting.valid(tenths)?tenths:0;}catch(NumberFormatException ignored){return 0;}}
    private void applySpeed(){
        int tenths=enteredSpeed();if(!saveSpeed.active||tenths==0)return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new DriverNetwork.SetSpeed(menu.containerId,tenths));
        speedEdited=false;speed.setFocused(false);
    }
    @Override protected void containerTick(){
        super.containerTick();int value=menu.value(21);
        if(value>0&&value!=syncedSpeed){
            if(!speedEdited){speed.setValue(String.format(java.util.Locale.ROOT,"%.1f",value/10.0));speedEdited=false;}
            syncedSpeed=value;
        }
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+302,topPos+272,0xffa48a50);g.fill(leftPos,topPos,leftPos+300,topPos+270,0xff252d32);
        g.drawString(font,title,leftPos+12,topPos+12,0xffe6cc8c,false);
        g.drawString(font,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),leftPos+12,topPos+35,0xff8ae2cd,false);
        g.drawString(font,text("power",String.format(java.util.Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),leftPos+12,topPos+53,0xffb9c5cc,false);
        String target=menu.details.target();
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.guidedrailways.auto.target",target.isEmpty()?"—":target).getString(),276),leftPos+12,topPos+76,0xffb9c5cc,false);
        if(menu.details.remainingTenths()>=0)g.drawString(font,Component.translatable("gui.guidedrailways.auto.remaining",String.format(java.util.Locale.ROOT,"%.1f",menu.details.remainingTenths()/10.0)),leftPos+160,topPos+35,0xffb9c5cc,false);
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.guidedrailways.auto.state."+menu.value(13)).getString(),276),leftPos+12,topPos+123,0xffe6cc8c,false);
        if(menu.value(12)!=0)g.drawString(font,Component.translatable("gui.guidedrailways.auto.progress",menu.value(14),menu.value(15)),leftPos+12,topPos+138,0xffb9c5cc,false);
        g.drawString(font,text("speed_at_256"),leftPos+12,topPos+217,0xffb9c5cc,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        forward.active=neutral.active=reverse.active=menu.value(11)!=0&&menu.value(19)==0;
        boolean schedule=menu.value(12)!=0&&menu.value(16)!=0;
        autoStart.active=schedule&&menu.value(17)==0;autoPause.active=schedule&&menu.value(17)!=0;
        autoSkip.active=schedule&&menu.value(18)!=0&&menu.value(13)!=ScheduleController.FINISHED;
        autoRestart.active=schedule&&menu.value(18)!=0;
        takeover.active=menu.value(16)!=0&&menu.value(20)!=0&&(menu.value(11)==0||menu.value(19)!=0);
        autoRemove.active=menu.value(12)!=0&&menu.value(18)!=0&&menu.value(20)!=0;
        boolean canEdit=menu.value(16)!=0&&menu.value(20)!=0;speed.setEditable(canEdit);saveSpeed.active=canEdit&&enteredSpeed()!=0;
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(isHovering(12,123,276,10,x,y))g.renderTooltip(font,java.util.List.of(Component.translatable("gui.guidedrailways.auto.state."+menu.value(13))),java.util.Optional.empty(),x,y);
        if(isHovering(12,76,276,10,x,y)&&!menu.details.target().isEmpty())g.renderTooltip(font,java.util.List.of(Component.translatable("gui.guidedrailways.auto.target",menu.details.target())),java.util.Optional.empty(),x,y);
        if(isHovering(12,211,156,20,x,y))g.renderTooltip(font,java.util.List.of(text("speed_help")),java.util.Optional.empty(),x,y);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){DriverClient.exit();onClose();return true;}if(speed.isFocused()&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)){applySpeed();return true;}return super.keyPressed(key,scan,mods);}
}
