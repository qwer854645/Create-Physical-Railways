package com.guidedrailways;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
    private final Button[] controls=new Button[9];
    private static final String[] KEYS={"take","slower","faster","forward","neutral","reverse","release","service","emergency"};
    DriverScreen(DriverMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=237;}
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    @Override protected void init(){
        super.init();
        for(int i=0;i<9;i++){
            int id=i,x,y,w;
            if(i==0){x=12;y=111;w=112;}else if(i<3){x=132+(i-1)*80;y=111;w=76;}else{x=12+(i-3)%3*94;y=i<6?140:169;w=88;}
            controls[i]=addRenderableWidget(Button.builder(text(KEYS[i]),b->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+y,w,20).build());
        }
    }
    private void line(GuiGraphics graphics,int y,Component text,int color){graphics.drawString(font,text,leftPos+12,topPos+y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xffa48a50);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff252d32);
        g.fill(leftPos+7,topPos+28,leftPos+293,topPos+105,0xff141c20);
        line(g,11,title,0xffe6cc8c);
        line(g,36,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",menu.value(4)/100.0)),0xff8ae2cd);
        line(g,54,text("target",menu.value(2)),0xffeeeeee);
        line(g,70,text("power",String.format(java.util.Locale.ROOT,"%.2f",menu.value(5)/100.0),menu.value(7),menu.value(6)),0xffb9c5cc);
        String state=menu.value(6)==0?"no_bogies":menu.value(8)<menu.value(6)?"derailed":menu.value(0)==0?"parked":menu.value(3)==2?"emergency_on":menu.value(3)==1?"braking":menu.value(9)>0?"mount_brake":menu.value(1)==0?"coasting":menu.value(7)==0?"no_power":"running";
        line(g,88,text(state),0xffe3c57d);
        line(g,202,text("scope"),0xff9baeb7);line(g,217,text("direction_hint"),0xff9baeb7);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        for(int i=0;i<controls.length;i++)if(controls[i]!=null){
            var label=text(i==0&&menu.value(0)!=0?"leave":KEYS[i]);
            boolean selected=i==3&&menu.value(1)==1||i==4&&menu.value(1)==0||i==5&&menu.value(1)==-1||i==6&&menu.value(3)==0||i==7&&menu.value(3)==1||i==8&&menu.value(3)==2;
            controls[i].setMessage(selected?Component.literal("• ").append(label):label);
            controls[i].active=i<6||i==8||menu.value(0)!=0;
        }
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
    }
}
