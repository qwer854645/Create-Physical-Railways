package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import java.util.*;
/** Settings and gear profiles share one server menu; neither page contains inventory slots. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private Button forward,neutral,reverse,autoStart,autoPause,autoSkip,autoRestart,takeover,autoRemove,gearTabButton,saveGears;
    private final List<AbstractWidget> mainWidgets=new ArrayList<>();
    private final EditBox[] gears=new EditBox[DriverModel.MAX_NOTCH];
    private final int[] syncedGears=new int[DriverModel.MAX_NOTCH];
    private boolean gearTab,gearsEdited;
    private int selectedGear;
    private GearLeverWidget lever;
    private GearSpeedSlider speedSlider;
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=270;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private Button command(Component label,int x,int y,int width,int id){
        var button=addRenderableWidget(Button.builder(label,b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+y,width,20).build());mainWidgets.add(button);return button;
    }
    @Override protected void init(){
        super.init();mainWidgets.clear();
        forward=command(text("forward"),12,96,86,3);neutral=command(text("neutral"),107,96,86,4);reverse=command(text("reverse"),202,96,86,5);
        autoStart=command(Component.translatable("gui.guidedrailways.auto.start"),12,155,86,30);
        autoPause=command(Component.translatable("gui.guidedrailways.auto.pause"),107,155,86,31);
        autoSkip=command(Component.translatable("gui.guidedrailways.auto.skip"),202,155,86,33);
        autoRestart=command(Component.translatable("gui.guidedrailways.auto.restart"),12,185,86,34);
        takeover=command(text("takeover"),107,185,86,35);
        autoRemove=command(Component.translatable("gui.guidedrailways.auto.remove"),202,185,86,32);
        gearTabButton=addRenderableWidget(Button.builder(text("gear_settings"),b->{gearTab=!gearTab;setFocused(null);}).bounds(leftPos+202,topPos+8,86,20).build());
        for(int i=0;i<gears.length;i++){
            String previous=gears[i]==null?"":gears[i].getValue();
            var box=new EditBox(font,leftPos+208,topPos+65,78,18,text("gear_label",i+1));box.setMaxLength(5);
            box.setFilter(value->value.matches("[0-9]{0,3}(\\.[0-9]?)?"));box.setValue(previous);
            box.setResponder(value->gearsEdited=true);gears[i]=addRenderableWidget(box);
        }
        Arrays.fill(syncedGears,-1);
        lever=addRenderableWidget(new GearLeverWidget(leftPos+12,topPos+53,86,141,()->selectedGear,this::selectGear));
        speedSlider=addRenderableWidget(new GearSpeedSlider(leftPos+110,topPos+104,178,this::selectedSpeed,this::sliderMaximum,this::dragSpeed));
        saveGears=addRenderableWidget(Button.builder(text("apply_gears"),b->applyGears()).bounds(leftPos+12,topPos+211,276,20).build());
        addRenderableWidget(Button.builder(text("close_settings"),b->onClose()).bounds(leftPos+12,topPos+241,276,20).build());
    }
    private void selectGear(int gear){gears[selectedGear].setFocused(false);selectedGear=Math.clamp(gear,0,gears.length-1);}
    private int selectedSpeed(){
        try{int value=(int)Math.round(Double.parseDouble(gears[selectedGear].getValue())*10);if(SpeedSetting.valid(value))return value;}catch(NumberFormatException ignored){}
        return Math.max(SpeedSetting.MIN,menu.value(22+selectedGear));
    }
    private int sliderMaximum(){
        int maximum=Math.max(SpeedSetting.MIN,menu.value(21));
        for(var box:gears)try{int value=(int)Math.round(Double.parseDouble(box.getValue())*10);if(SpeedSetting.valid(value))maximum=Math.max(maximum,value);}catch(NumberFormatException ignored){}
        return maximum;
    }
    private void dragSpeed(int value){
        int[] profile=enteredGears();if(profile==null)return;
        value=GearEditorModel.betweenNeighbours(value,selectedGear,profile);
        gears[selectedGear].setValue(String.format(Locale.ROOT,"%.1f",value/10.0));
    }
    private int[] enteredGears(){
        int[] values=new int[gears.length];
        try{for(int i=0;i<values.length;i++)values[i]=(int)Math.round(Double.parseDouble(gears[i].getValue())*10);}
        catch(NumberFormatException ignored){return null;}
        return SpeedSetting.validGears(SpeedSetting.MAX,values)?values:null;
    }
    private void applyGears(){
        var values=enteredGears();if(!saveGears.active||values==null)return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new DriverNetwork.SetGears(menu.containerId,values));
        gearsEdited=false;setFocused(null);Arrays.fill(syncedGears,-1);
    }
    @Override protected void containerTick(){
        super.containerTick();
        for(int i=0;i<gears.length;i++){
            int value=menu.value(22+i);
            if(value>0&&value!=syncedGears[i]&&!gearsEdited){gears[i].setValue(String.format(Locale.ROOT,"%.1f",value/10.0));gearsEdited=false;}
            syncedGears[i]=value;
        }
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+302,topPos+272,0xffa48a50);g.fill(leftPos,topPos,leftPos+300,topPos+270,0xff252d32);
        g.drawString(font,title,leftPos+12,topPos+12,0xffe6cc8c,false);
        if(gearTab){
            g.drawString(font,text("lever_instructions"),leftPos+12,topPos+36,0xffb9c5cc,false);
            g.drawString(font,text("selected_gear",selectedGear+1),leftPos+110,topPos+66,0xff8ae2cd,false);
            g.drawString(font,text("speed_units"),leftPos+110,topPos+83,0xffb9c5cc,false);
            g.drawString(font,"0.1",leftPos+110,topPos+127,0xff8e9699,false);
            String max=String.format(Locale.ROOT,"%.1f",sliderMaximum()/10.0);
            g.drawString(font,max,leftPos+288-font.width(max),topPos+127,0xff8e9699,false);
            for(int i=0;i<gears.length;i++){
                int x=108+(i<4?0:92),y=140+(i%4)*14;
                if(i==selectedGear)g.fill(leftPos+x-2,topPos+y-2,leftPos+x+88,topPos+y+11,0xff3e514e);
                g.drawString(font,"P"+(i+1)+"  "+gears[i].getValue(),leftPos+x,topPos+y,i==selectedGear?0xff8ae2cd:0xffb9c5cc,false);
            }
            g.drawString(font,font.plainSubstrByWidth(text("gears_order").getString(),276),leftPos+12,topPos+199,0xffb9c5cc,false);return;
        }
        g.drawString(font,text("actual",String.format(Locale.ROOT,"%+.2f",menu.value(4)/100.0)),leftPos+12,topPos+35,0xff8ae2cd,false);
        g.drawString(font,text("power",String.format(Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),leftPos+12,topPos+53,0xffb9c5cc,false);
        String target=menu.details.target();
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.guidedrailways.auto.target",target.isEmpty()?"—":target).getString(),276),leftPos+12,topPos+76,0xffb9c5cc,false);
        if(menu.details.remainingTenths()>=0)g.drawString(font,Component.translatable("gui.guidedrailways.auto.remaining",String.format(Locale.ROOT,"%.1f",menu.details.remainingTenths()/10.0)),leftPos+160,topPos+35,0xffb9c5cc,false);
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.guidedrailways.auto.state."+menu.value(13)).getString(),276),leftPos+12,topPos+123,0xffe6cc8c,false);
        if(menu.value(12)!=0)g.drawString(font,Component.translatable("gui.guidedrailways.auto.progress",menu.value(14),menu.value(15)),leftPos+12,topPos+138,0xffb9c5cc,false);
        g.drawString(font,text("configured_max",String.format(Locale.ROOT,"%.1f",menu.value(21)/10.0)),leftPos+12,topPos+217,0xffb9c5cc,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        for(var widget:mainWidgets)widget.visible=!gearTab;
        gearTabButton.setMessage(text(gearTab?"back_settings":"gear_settings"));
        for(int i=0;i<gears.length;i++){gears[i].visible=gearTab&&i==selectedGear;gears[i].setEditable(menu.value(16)!=0&&menu.value(20)!=0);}
        lever.visible=speedSlider.visible=gearTab;speedSlider.active=menu.value(16)!=0&&menu.value(20)!=0&&enteredGears()!=null;speedSlider.refresh();
        saveGears.visible=gearTab;saveGears.active=menu.value(16)!=0&&menu.value(20)!=0&&enteredGears()!=null;
        forward.active=neutral.active=reverse.active=menu.value(11)!=0&&menu.value(19)==0;
        boolean schedule=menu.value(12)!=0&&menu.value(16)!=0;
        autoStart.active=schedule&&menu.value(17)==0;autoPause.active=schedule&&menu.value(17)!=0;
        autoSkip.active=schedule&&menu.value(18)!=0&&menu.value(13)!=ScheduleController.FINISHED;
        autoRestart.active=schedule&&menu.value(18)!=0;
        takeover.active=menu.value(16)!=0&&menu.value(20)!=0&&(menu.value(11)==0||menu.value(19)!=0);
        autoRemove.active=menu.value(12)!=0&&menu.value(18)!=0&&menu.value(20)!=0;
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(!gearTab&&isHovering(12,123,276,10,x,y))g.renderTooltip(font,List.of(Component.translatable("gui.guidedrailways.auto.state."+menu.value(13))),Optional.empty(),x,y);
        if(!gearTab&&isHovering(12,76,276,10,x,y)&&!menu.details.target().isEmpty())g.renderTooltip(font,List.of(Component.translatable("gui.guidedrailways.auto.target",menu.details.target())),Optional.empty(),x,y);
        if(isHovering(12,gearTab?195:211,276,20,x,y))g.renderTooltip(font,List.of(text("gears_help")),Optional.empty(),x,y);
        if(gearTab&&!isDragging()&&lever.isHoveredOrFocused())g.renderTooltip(font,List.of(text("lever_help")),Optional.empty(),x,y);
        if(gearTab&&!isDragging()&&speedSlider.isHoveredOrFocused())g.renderTooltip(font,List.of(text("slider_help")),Optional.empty(),x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(gearTab&&button==0)for(int i=0;i<gears.length;i++)if(isHovering(106+(i<4?0:92),138+(i%4)*14,90,13,x,y)){selectGear(i);setFocused(lever);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(gearTab){gearTab=false;setFocused(null);}else{DriverClient.exit();onClose();}return true;}
        if(gearTab&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)){applyGears();return true;}
        return super.keyPressed(key,scan,mods);
    }
}
