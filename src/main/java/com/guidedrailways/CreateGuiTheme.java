package com.guidedrailways;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
/** Shared Create GUI materials. Atlas pieces are tiled at native pixel resolution. */
final class CreateGuiTheme {
    static final int TEXT=0xffc6c6c6,DIM=0xff9b9b9b,ACCENT=0xffe4b763,HEADER=0xff000000|AllGuiTextures.FONT_COLOR;
    static final int BODY=0xff575757,RECESS=0xff303030;
    private static void tile(GuiGraphics g,ResourceLocation atlas,int x,int y,int w,int h,int u,int v,int sw,int sh){
        for(int dy=0;dy<h;dy+=sh)for(int dx=0;dx<w;dx+=sw)
            g.blit(atlas,x+dx,y+dy,u,v,Math.min(sw,w-dx),Math.min(sh,h-dy));
    }
    static void panel(GuiGraphics g,int x,int y,int width,int height){
        var atlas=AllGuiTextures.SCHEDULE_EDITOR.location;
        g.fill(x+8,y+14,x+width-8,y+height-14,BODY);
        // Clean border pieces from the native schedule editor, excluding its baked controls.
        tile(g,atlas,x,y,8,15,4,0,8,15);tile(g,atlas,x+width-8,y,8,15,244,0,8,15);
        tile(g,atlas,x+8,y,width-16,15,12,0,232,15);
        tile(g,atlas,x,y+height-15,8,15,4,74,8,15);tile(g,atlas,x+width-8,y+height-15,8,15,244,74,8,15);
        tile(g,atlas,x+8,y+height-15,width-16,15,12,74,232,15);
        tile(g,atlas,x,y+15,8,height-30,4,15,8,59);tile(g,atlas,x+width-8,y+15,8,height-30,244,15,8,59);
    }
    static void inset(GuiGraphics g,int x,int y,int width,int height){
        g.fill(x,y,x+width,y+height,0xff858585);g.fill(x,y,x+width-1,y+height-1,0xff171717);
        g.fill(x+1,y+1,x+width-1,y+height-1,RECESS);
    }
    static void button(GuiGraphics g,AllGuiTextures texture,int x,int y,int width){
        int u=texture.getStartX(),v=texture.getStartY();
        tile(g,texture.location,x,y,3,18,u,v,3,18);
        tile(g,texture.location,x+3,y,width-6,18,u+3,v,12,18);
        tile(g,texture.location,x+width-3,y,3,18,u+15,v,3,18);
    }
    static String fit(Font font,String text,int width){
        return font.width(text)<=width?text:font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";
    }
}
