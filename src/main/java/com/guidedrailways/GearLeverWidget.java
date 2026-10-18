package com.guidedrailways;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.*;
/** A detented throttle illustration; manipulating it only selects an editor row. */
final class GearLeverWidget extends AbstractWidget {
    private final IntSupplier selected;private final IntConsumer select;
    GearLeverWidget(int x,int y,int width,int height,IntSupplier selected,IntConsumer select){
        super(x,y,width,height,Component.translatable("gui.guidedrailways.driver.gear_settings"));this.selected=selected;this.select=select;
    }
    private int detent(int index){return getY()+height-12-(int)Math.round(index*(height-24.0)/(DriverModel.MAX_NOTCH-1));}
    private void pick(double y){select.accept(GearEditorModel.selection(y,getY()+12,getY()+height-12));}
    @Override public void onClick(double x,double y){pick(y);}
    @Override protected void onDrag(double x,double y,double dx,double dy){pick(y);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!active||!visible)return false;
        int current=selected.getAsInt();
        if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_RIGHT)select.accept(Math.min(7,current+1));
        else if(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_LEFT)select.accept(Math.max(0,current-1));
        else if(key==GLFW.GLFW_KEY_HOME)select.accept(0);else if(key==GLFW.GLFW_KEY_END)select.accept(7);else return false;
        return true;
    }
    @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        int x=getX(),y=getY(),cx=x+29;
        g.fill(x,y,x+width,y+height,isHoveredOrFocused()?0xffc4a666:0xff897344);
        g.fill(x+2,y+2,x+width-2,y+height-2,0xff343c40);
        g.fill(cx-6,y+7,cx+7,y+height-7,0xff171d20);
        g.fill(cx-4,y+8,cx-2,y+height-8,0xff596166);
        for(int i=0;i<DriverModel.MAX_NOTCH;i++){
            int cy=detent(i);boolean chosen=i==selected.getAsInt();
            g.fill(cx+11,cy-1,cx+19,cy+1,chosen?0xff8ae2cd:0xff8e9699);
            g.drawString(Minecraft.getInstance().font,"P"+(i+1),cx+24,cy-4,chosen?0xff8ae2cd:0xffb9c5cc,false);
        }
        int knob=detent(selected.getAsInt()),base=y+height-10;
        g.fill(cx-2,knob,cx+3,base,0xffc8ced0);g.fill(cx+1,knob,cx+3,base,0xff6b7478);
        g.fill(cx-7,base-4,cx+8,base+4,0xffa48a50);g.fill(cx-3,base-2,cx+4,base+2,0xff22292d);
        g.fill(cx-19,knob-7,cx+20,knob+7,0xff10171b);
        g.fill(cx-17,knob-6,cx+18,knob+5,0xffa48a50);
        g.fill(cx-13,knob-5,cx+14,knob+4,0xff444e54);
        g.fill(cx-12,knob-4,cx+13,knob-2,0xff758087);
        for(int ridge=-8;ridge<=8;ridge+=4)g.fill(cx+ridge,knob-1,cx+ridge+1,knob+4,0xff20292e);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        output.add(NarratedElementType.TITLE,Component.translatable("gui.guidedrailways.driver.selected_gear",selected.getAsInt()+1));
        output.add(NarratedElementType.USAGE,Component.translatable("gui.guidedrailways.driver.lever_help"));
    }
}
