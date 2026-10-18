package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
/** Discrete keys, wheel and draggable detents all use the same validated menu commands. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private Button take,push,pull,centre,service,forward,neutral,reverse,emergency;
    private boolean pushHeld,pullHeld,dragging;
    private int lastDragged=99;
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=254;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private void send(int command){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,command);}
    private Button button(String key,int x,int y,int width,int command){return addRenderableWidget(Button.builder(text(key),b->send(command)).bounds(leftPos+x,topPos+y,width,20).build());}
    @Override protected void init(){
        super.init();
        take=button("take",104,88,224,0);
        push=button("push",104,115,108,2);pull=button("pull",220,115,108,1);
        centre=button("centre",104,142,108,6);service=button("full_service",220,142,108,7);
        forward=button("forward",104,169,70,3);neutral=button("neutral",181,169,70,4);reverse=button("reverse",258,169,70,5);
        emergency=addRenderableWidget(Button.builder(text("emergency"),b->send(menu.value(3)!=0?9:8)).bounds(leftPos+104,topPos+196,224,20).build());
        pushHeld=pullHeld=dragging=false;lastDragged=99;
    }
    private void line(GuiGraphics g,int y,Component value,int color){g.drawString(font,value,leftPos+12,topPos+y,color,false);}
    private Component notchName(){int n=menu.value(2);return text(n>0?"traction_notch":n<0?"brake_notch":"coasting",Math.abs(n),Math.abs(n)*25);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xffa48a50);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff252d32);
        g.fill(leftPos+7,topPos+26,leftPos+333,topPos+80,0xff141c20);
        line(g,10,title,0xffe6cc8c);
        line(g,32,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),0xff8ae2cd);
        line(g,48,text("power",String.format(java.util.Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),0xffb9c5cc);
        String state=menu.value(6)==0?"no_bogies":menu.value(3)!=0?"emergency_on":menu.value(8)<menu.value(6)?"derailed":menu.value(0)==0?"parked":menu.value(9)>0?"mount_brake":menu.value(7)==0?"no_power":"lever_active";
        line(g,64,text(state),0xffe3c57d);
        g.fill(leftPos+12,topPos+87,leftPos+93,topPos+220,0xff141c20);
        g.fill(leftPos+48,topPos+95,leftPos+52,topPos+209,0xff887552);
        for(int n=-4;n<=4;n++){
            int y=topPos+151-n*14;int color=n>0?0xff77b6a0:n<0?0xffd58c72:0xffe6cc8c;
            g.fill(leftPos+31,y,leftPos+70,y+1,color);
            g.drawString(font,n==0?"0":Integer.toString(Math.abs(n)),leftPos+18,y-3,color,false);
        }
        int handle=topPos+151-menu.value(2)*14;
        g.fill(leftPos+33,handle-5,leftPos+82,handle+6,0xffb7a16e);g.fill(leftPos+36,handle-3,leftPos+79,handle+3,0xffe3d19b);
        line(g,226,notchName(),0xff8ae2cd);line(g,240,text("lever_hint"),0xff9baeb7);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    private void updateButtons(){
        boolean active=menu.value(0)!=0,locked=menu.value(3)!=0;
        take.setMessage(text(active?"leave":"take"));
        push.active=pull.active=centre.active=service.active=active&&!locked;
        forward.active=neutral.active=reverse.active=active&&!locked;
        forward.setMessage(menu.value(1)==1?Component.literal("• ").append(text("forward")):text("forward"));
        neutral.setMessage(menu.value(1)==0?Component.literal("• ").append(text("neutral")):text("neutral"));
        reverse.setMessage(menu.value(1)==-1?Component.literal("• ").append(text("reverse")):text("reverse"));
        emergency.setMessage(text(locked?"reset_emergency":"emergency"));emergency.active=!locked||active;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){updateButtons();super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_W){if(!pushHeld&&menu.value(0)!=0&&menu.value(3)==0)send(2);pushHeld=true;return true;}
        if(key==GLFW.GLFW_KEY_S){if(!pullHeld&&menu.value(0)!=0&&menu.value(3)==0)send(1);pullHeld=true;return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_W){pushHeld=false;return true;}if(key==GLFW.GLFW_KEY_S){pullHeld=false;return true;}return super.keyReleased(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(menu.value(0)!=0&&menu.value(3)==0&&Math.abs(vertical)>.01){send(vertical>0?2:1);return true;}return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private void drag(double y){int notch=Math.clamp((int)Math.round((topPos+151-y)/14),-4,4);if(notch!=lastDragged){send(14+notch);lastDragged=notch;}}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&x>=leftPos+12&&x<=leftPos+93&&y>=topPos+87&&y<=topPos+220&&menu.value(0)!=0&&menu.value(3)==0){dragging=true;drag(y);return true;}return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){drag(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragging){dragging=false;lastDragged=99;return true;}return super.mouseReleased(x,y,button);}
}
