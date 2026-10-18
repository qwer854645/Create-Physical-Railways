package com.guidedrailways;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
/** No Screen for takeover: normal camera, crosshair, inventory and hotbar remain vanilla. */
public final class DriverClient {
    private static DriverNetwork.State state=DriverNetwork.State.off();
    private static boolean ready,pending,grabbing,resetRequested;
    private static int steer,ticks,heldTicks,resetTicks,requestedNotch;
    private static double dragPixels;
    private static long lastPacket;
    private DriverClient(){}
    static void register(){
        DriverNetwork.clientHandler=DriverClient::accept;
        NeoForge.EVENT_BUS.addListener(DriverClient::movement);
        NeoForge.EVENT_BUS.addListener(DriverClient::mouse);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,DriverClient::turn);
        NeoForge.EVENT_BUS.addListener(DriverClient::scroll);
        NeoForge.EVENT_BUS.addListener(DriverClient::key);
        NeoForge.EVENT_BUS.addListener(DriverClient::tick);
        NeoForge.EVENT_BUS.addListener(DriverClient::hud);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->clear());
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e)->{
            if(state.active()&&Minecraft.getInstance().screen==null&&e.isAttack()){e.setCanceled(true);e.setSwingHand(false);send(8);}
        });
    }
    private static void clear(){state=DriverNetwork.State.off();ready=pending=grabbing=resetRequested=false;steer=heldTicks=resetTicks=0;dragPixels=0;}
    static void accept(DriverNetwork.State next){
        boolean newSession=next.active()&&(!state.active()||!next.pos().equals(state.pos()));
        if(next.observing()!=state.observing()){pending=grabbing=false;heldTicks=resetTicks=0;steer=0;}
        state=next;lastPacket=System.nanoTime();
        if(!next.active()){clear();return;}
        if(newSession){
            ready=pending=grabbing=false;steer=0;
            var mc=Minecraft.getInstance();mc.options.keyAttack.setDown(false);if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();
        }
    }
    static void send(int command){if(state.active()&&Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new DriverNetwork.Command(state.pos(),command));}
    static void exit(){send(19);clear();}
    private static boolean aimingConsole(){
        var mc=Minecraft.getInstance();return mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(state.pos());
    }
    private static boolean rightDown(){var mc=Minecraft.getInstance();return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;}
    private static void movement(MovementInputUpdateEvent event){
        var mc=Minecraft.getInstance();if(!DriverInputPolicy.suppressMovement(state.active(),mc.screen!=null)||event.getEntity()!=mc.player)return;
        var in=event.getInput();int next=state.observing()?0:DriverInputPolicy.steer(in.left,in.right);
        if(next!=steer){steer=next;send(steer<0?20:steer>0?21:22);}
        in.forwardImpulse=0;in.leftImpulse=0;in.up=in.down=in.left=in.right=false;
        // Jumping and crouching are intentionally left untouched.
    }
    private static void mouse(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();if(!state.active()||mc.screen!=null)return;
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            mc.options.keyAttack.setDown(false);if(event.getAction()==GLFW.GLFW_PRESS)send(8);event.setCanceled(true);return;
        }
        if(event.getButton()!=GLFW.GLFW_MOUSE_BUTTON_RIGHT||state.observing())return;
        if(event.getAction()==GLFW.GLFW_RELEASE){
            ready=true;if(pending||grabbing){pending=grabbing=false;heldTicks=resetTicks=0;event.setCanceled(true);}return;
        }
        if(event.getAction()==GLFW.GLFW_PRESS&&ready&&aimingConsole()&&!net.minecraft.client.gui.screens.Screen.hasShiftDown()){
            pending=true;grabbing=false;heldTicks=resetTicks=0;resetRequested=false;event.setCanceled(true);
        }
    }
    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){clear();return;}
        if(!state.active())return;
        if(System.nanoTime()-lastPacket>5_000_000_000L){send(19);clear();return;}
        ticks++;
        if(mc.screen!=null||!mc.isWindowActive()){
            pending=grabbing=false;heldTicks=0;steer=0;
        }
        if(!rightDown()){ready=true;pending=grabbing=false;heldTicks=resetTicks=0;}
        if(pending&&!grabbing){
            if(!aimingConsole()||net.minecraft.client.gui.screens.Screen.hasShiftDown())pending=false;
            else if(++heldTicks>=4){grabbing=true;requestedNotch=state.notch();dragPixels=0;}
        }
        if(grabbing&&state.emergency()&&!resetRequested&&++resetTicks>=20){send(9);resetRequested=true;}
        if(ticks%10==0)send(steer<0?20:steer>0?21:22);
    }
    private static void turn(CalculatePlayerTurnEvent event){
        var mc=Minecraft.getInstance();if(!DriverInputPolicy.suppressLook(state.active(),grabbing,mc.screen!=null,rightDown()))return;
        if(!state.emergency()){
            dragPixels-=mc.mouseHandler.getYVelocity()/mc.getWindow().getGuiScale();
            int delta=(int)(dragPixels/18);if(delta!=0){int next=Math.clamp(requestedNotch+delta,-DriverModel.BRAKE_NOTCHES,DriverModel.MAX_NOTCH);dragPixels-=delta*18;if(next!=requestedNotch){requestedNotch=next;send(DriverInputPolicy.NOTCH_BASE+next);}}
        }
        // Only this held-right drag consumes looking. Release restores the original event values.
        event.setCinematicCameraEnabled(false);event.setMouseSensitivity(DriverInputPolicy.heldMouseSensitivity());
    }
    private static void scroll(InputEvent.MouseScrollingEvent event){
        if(!state.active()||!grabbing||Minecraft.getInstance().screen!=null)return;
        event.setCanceled(true);if(!state.emergency()&&Math.abs(event.getScrollDeltaY())>.01){requestedNotch=DriverModel.step(requestedNotch,event.getScrollDeltaY()>0?1:-1);send(DriverInputPolicy.NOTCH_BASE+requestedNotch);}
    }
    private static void key(InputEvent.Key event){
        var mc=Minecraft.getInstance();
        if(state.active()&&event.getKey()==GLFW.GLFW_KEY_ESCAPE&&event.getAction()==GLFW.GLFW_PRESS&&(mc.screen==null||mc.screen instanceof PauseScreen)){
            exit();if(mc.screen instanceof PauseScreen)mc.setScreen(null);
        }
    }
    private static Component text(String key,Object... args){return Component.translatable("gui.guidedrailways.driver."+key,args);}
    private static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!state.active()||mc.screen!=null||mc.options.hideGui)return;
        var g=event.getGuiGraphics();int x=12,y=mc.getWindow().getGuiScaledHeight()-92;
        CreateGuiTheme.panel(g,x-5,y-6,310,90);
        g.drawString(mc.font,text("actual",String.format(java.util.Locale.ROOT,"%+.2f",state.speed())),x,y,CreateGuiTheme.HEADER,false);
        if(state.observing()){
            g.drawString(mc.font,text(state.emergency()?"emergency_on":"observing"),x,y+15,CreateGuiTheme.ACCENT,false);
            g.drawString(mc.font,text("takeover_hint"),x,y+35,CreateGuiTheme.TEXT,false);
            g.drawString(mc.font,text("observe_keys"),x,y+55,CreateGuiTheme.DIM,false);return;
        }
        int n=state.notch();g.drawString(mc.font,state.emergency()?text("emergency_on"):n>0?text("traction_speed",n,String.format(java.util.Locale.ROOT,"%.1f",state.gearTarget())):text(n<0?"brake_notch":"coasting",Math.abs(n),Math.abs(n)*25),x,y+15,CreateGuiTheme.ACCENT,false);
        g.drawString(mc.font,text(steer<0?"steer_left":steer>0?"steer_right":"steer_default"),x,y+30,CreateGuiTheme.TEXT,false);
        g.drawString(mc.font,text(state.emergency()?"reset_hint":grabbing?"dragging_hint":"world_hint"),x,y+45,CreateGuiTheme.TEXT,false);
        g.drawString(mc.font,text("world_keys"),x,y+60,CreateGuiTheme.DIM,false);
    }
}
