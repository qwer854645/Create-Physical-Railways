package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
/** Full GUI is settings only; driving stays in the world with a small HUD and held-right drag. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private Button forward,neutral,reverse,back;
    private boolean leftHeld,rightHeld,grabbing,ready,resetRequested;
    private double dragPixels;private int requestedNotch,ticks,resetTicks;
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=155;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private void send(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private boolean settings(){return menu.value(10)!=0;}
    private Button button(String key,int x,int width,int id){return addRenderableWidget(Button.builder(text(key),b->send(id)).bounds(leftPos+x,topPos+96,width,20).build());}
    @Override protected void init(){
        super.init();forward=button("forward",12,86,3);neutral=button("neutral",107,86,4);reverse=button("reverse",202,86,5);
        back=addRenderableWidget(Button.builder(text("back_control"),b->send(24)).bounds(leftPos+12,topPos+125,276,20).build());
        grabbing=ready=leftHeld=rightHeld=resetRequested=false;resetTicks=0;
    }
    @Override protected void containerTick(){
        super.containerTick();ticks++;
        if(GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_RELEASE){ready=true;grabbing=false;resetTicks=0;resetRequested=false;}
        if(!settings()&&menu.value(11)!=0){
            if(ticks%10==0)sendSteer();
            if(grabbing&&menu.value(3)!=0&&!resetRequested&&++resetTicks>=20){send(9);resetRequested=true;}
        }
    }
    private void sendSteer(){send(!settings()&&leftHeld!=rightHeld?(leftHeld?20:21):22);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){if(settings())g.fill(0,0,width,height,0x88000000);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        if(settings()){
            g.fill(leftPos-2,topPos-2,leftPos+302,topPos+157,0xffa48a50);g.fill(leftPos,topPos,leftPos+300,topPos+155,0xff252d32);
            g.drawString(font,title,leftPos+12,topPos+12,0xffe6cc8c,false);
            g.drawString(font,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),leftPos+12,topPos+35,0xff8ae2cd,false);
            g.drawString(font,text("power",String.format(java.util.Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),leftPos+12,topPos+53,0xffb9c5cc,false);
            g.drawString(font,text("settings_only"),leftPos+12,topPos+76,0xffb9c5cc,false);return;
        }
        int x=12,y=height-91;g.fill(x-5,y-6,x+300,y+78,0xcc141c20);
        g.drawString(font,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),x,y,0xff8ae2cd,false);
        int n=menu.value(2);var label=menu.value(3)!=0?text("emergency_on"):text(n>0?"traction_notch":n<0?"brake_notch":"coasting",Math.abs(n),Math.abs(n)*25);
        g.drawString(font,label,x,y+15,0xffe6cc8c,false);
        g.drawString(font,text(leftHeld!=rightHeld?(leftHeld?"steer_left":"steer_right"):"steer_default"),x,y+30,0xffd5dbe0,false);
        g.drawString(font,text(grabbing?"dragging_hint":"world_hint"),x,y+45,0xffb9c5cc,false);
        g.drawString(font,text("world_keys"),x,y+60,0xffb9c5cc,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        boolean show=settings();forward.visible=neutral.visible=reverse.visible=back.visible=show;
        forward.active=neutral.active=reverse.active=menu.value(11)!=0;back.active=menu.value(11)!=0;
        super.render(g,x,y,partial);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){send(19);onClose();return true;}
        if(!settings()){
            if(key==GLFW.GLFW_KEY_A){leftHeld=true;sendSteer();return true;}
            if(key==GLFW.GLFW_KEY_D){rightHeld=true;sendSteer();return true;}
            // W/S no longer adjust the throttle; the modal also prevents player movement.
            if(key==GLFW.GLFW_KEY_W||key==GLFW.GLFW_KEY_S)return true;
            if(key==GLFW.GLFW_KEY_E)return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_A){leftHeld=false;sendSteer();return true;}if(key==GLFW.GLFW_KEY_D){rightHeld=false;sendSteer();return true;}return super.keyReleased(key,scan,mods);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(settings())return super.mouseClicked(x,y,button);
        if(button==0){send(8);return true;}
        if(button==1&&hasShiftDown()){grabbing=false;leftHeld=rightHeld=false;send(22);send(23);return true;}
        if(button==1&&ready&&menu.value(11)!=0){grabbing=true;dragPixels=0;requestedNotch=menu.value(2);resetTicks=0;resetRequested=false;return true;}
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(!settings()&&button==1&&grabbing){
            if(menu.value(3)!=0)return true;dragPixels-=dy;
            while(Math.abs(dragPixels)>=18){int step=dragPixels>0?1:-1;requestedNotch=DriverModel.step(requestedNotch,step);send(14+requestedNotch);dragPixels-=step*18;}return true;
        }return settings()&&super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(!settings()&&button==1){grabbing=false;ready=true;resetTicks=0;return true;}return settings()&&super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!settings()&&grabbing&&menu.value(3)==0&&Math.abs(vertical)>.01){send(vertical>0?2:1);return true;}return settings()&&super.mouseScrolled(x,y,horizontal,vertical);}
}
