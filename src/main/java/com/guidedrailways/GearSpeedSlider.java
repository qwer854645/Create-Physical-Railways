package com.guidedrailways;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.function.*;
import org.lwjgl.glfw.GLFW;
final class GearSpeedSlider extends AbstractSliderButton {
    private final IntSupplier speed,maximum;private final IntConsumer edit;
    GearSpeedSlider(int x,int y,int width,IntSupplier speed,IntSupplier maximum,IntConsumer edit){
        super(x,y,width,20,Component.empty(),0);this.speed=speed;this.maximum=maximum;this.edit=edit;refresh();
    }
    void refresh(){value=GearEditorModel.fraction(speed.getAsInt(),maximum.getAsInt());updateMessage();}
    @Override protected void updateMessage(){setMessage(Component.translatable("gui.guidedrailways.driver.slider_speed",String.format(Locale.ROOT,"%.1f",speed.getAsInt()/10.0)));}
    @Override protected void applyValue(){edit.accept(GearEditorModel.speed(value,maximum.getAsInt()));refresh();}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!active||!visible)return false;
        if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){edit.accept(Math.clamp(speed.getAsInt()+(key==GLFW.GLFW_KEY_RIGHT?1:-1),SpeedSetting.MIN,maximum.getAsInt()));refresh();return true;}
        return super.keyPressed(key,scan,mods);
    }
}
