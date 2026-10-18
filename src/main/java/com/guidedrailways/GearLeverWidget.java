package com.guidedrailways;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.*;
import com.simibubi.create.foundation.gui.AllGuiTextures;
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
        CreateGuiTheme.inset(g,x,y,width,height);
        if(isHoveredOrFocused())g.fill(x+2,y+2,x+width-2,y+3,CreateGuiTheme.ACCENT);
        g.fill(cx-6,y+7,cx+7,y+height-7,0xff171717);
        g.fill(cx-4,y+8,cx-2,y+height-8,0xff858585);
        for(int i=0;i<DriverModel.MAX_NOTCH;i++){
            int cy=detent(i);boolean chosen=i==selected.getAsInt();
            g.fill(cx+11,cy-1,cx+19,cy+1,chosen?CreateGuiTheme.ACCENT:CreateGuiTheme.DIM);
            g.drawString(Minecraft.getInstance().font,"P"+(i+1),cx+24,cy-4,chosen?CreateGuiTheme.ACCENT:CreateGuiTheme.TEXT,false);
        }
        int knob=detent(selected.getAsInt()),base=y+height-10;
        g.fill(cx-2,knob,cx+3,base,CreateGuiTheme.TEXT);g.fill(cx+1,knob,cx+3,base,0xff858585);
        g.fill(cx-7,base-4,cx+8,base+4,0xff858585);g.fill(cx-3,base-2,cx+4,base+2,0xff171717);
        CreateGuiTheme.button(g,isHoveredOrFocused()?AllGuiTextures.BUTTON_HOVER:AllGuiTextures.BUTTON,cx-19,knob-9,39);
        for(int ridge=-8;ridge<=8;ridge+=4)g.fill(cx+ridge,knob-3,cx+ridge+1,knob+4,0xff575757);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        output.add(NarratedElementType.TITLE,Component.translatable("gui.guidedrailways.driver.selected_gear",selected.getAsInt()+1));
        output.add(NarratedElementType.USAGE,Component.translatable("gui.guidedrailways.driver.lever_help"));
    }
}
