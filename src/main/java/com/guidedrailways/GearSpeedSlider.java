package com.guidedrailways;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.function.*;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
final class GearSpeedSlider extends AbstractSliderButton {
    private final IntSupplier speed,maximum;private final IntConsumer edit;
    GearSpeedSlider(int x,int y,int width,IntSupplier speed,IntSupplier maximum,IntConsumer edit){
        super(x,y,width,20,Component.empty(),0);this.speed=speed;this.maximum=maximum;this.edit=edit;refresh();
    }
    void refresh(){value=GearEditorModel.fraction(speed.getAsInt(),maximum.getAsInt());updateMessage();}
    @Override protected void updateMessage(){setMessage(Component.translatable("gui.guidedrailways.driver.slider_speed",String.format(Locale.ROOT,"%.1f",speed.getAsInt()/10.0)));}
    @Override protected void applyValue(){edit.accept(GearEditorModel.speed(value,maximum.getAsInt()));refresh();}
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        int x=getX(),y=getY(),knob=x+(int)Math.round(value*(width-8));
        CreateGuiTheme.inset(g,x,y+7,width,6);
        g.fill(x+2,y+9,Math.max(x+2,knob+4),y+11,active?CreateGuiTheme.ACCENT:CreateGuiTheme.DIM);
        CreateGuiTheme.button(g,!active?AllGuiTextures.BUTTON_DISABLED:isHoveredOrFocused()?AllGuiTextures.BUTTON_HOVER:AllGuiTextures.BUTTON,knob,y+1,8);
        g.fill(knob+3,y+6,knob+5,y+14,0xff575757);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!active||!visible)return false;
        if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){edit.accept(Math.clamp(speed.getAsInt()+(key==GLFW.GLFW_KEY_RIGHT?1:-1),SpeedSetting.MIN,maximum.getAsInt()));refresh();return true;}
        return super.keyPressed(key,scan,mods);
    }
}
