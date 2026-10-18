package com.guidedrailways;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Only sneak-right settings creates a menu. Driving is independent of menu lifetime. */
public final class DriverMenu extends AbstractContainerMenu {
    private final DriverConsoleEntity console;private final ContainerData data;
    private final Player viewer;private long nextDetails;private DriverNetwork.MenuState sentDetails;
    DriverNetwork.MenuState details=new DriverNetwork.MenuState(-1,"",-1);
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console){
        super(Content.DRIVER_MENU.get(),id);this.console=console;viewer=inventory.player;
        data=console==null?new SimpleContainerData(30):new ContainerData(){
            @Override public int get(int i){if(i>=22&&i<30)return console.gearSpeeds()[i-22];return switch(i){case 0->console.active?1:0;case 1->console.direction;case 2->console.notch;case 3->console.emergency?1:0;case 4->(int)Math.clamp(console.actual*100,-32000,32000);case 5->(int)Math.clamp(console.available*100,0,32000);case 6->console.supports;case 7->console.powered;case 8->console.guided;case 9->console.mountBrakes;case 10->1;case 11->inventory.player.getUUID().equals(console.operator)&&DriverControls.primaryView(console)?1:0;case 12->console.schedule.present()?1:0;case 13->DriverControls.primaryView(console)?console.schedule.status:15;case 14->Math.min(console.schedule.total(),console.schedule.entry+1);case 15->console.schedule.total();case 16->DriverControls.primaryView(console)?1:0;case 17->console.schedule.running?1:0;case 18->DriverControls.stopped(console)?1:0;case 19->console.observing?1:0;case 20->console.operator==null||inventory.player.getUUID().equals(console.operator)?1:0;case 21->console.maxSpeedTenths();default->0;};}
            @Override public void set(int i,int value){}
            @Override public int getCount(){return 30;}
        };addDataSlots(data);
    }
    int value(int index){return data.get(index);}
    void setGears(Player player,int[] tenths){if(console!=null&&stillValid(player)&&console.setGearSpeeds(player,tenths))broadcastChanges();}
    @Override public void broadcastChanges(){
        super.broadcastChanges();
        if(console==null||!(viewer instanceof net.minecraft.server.level.ServerPlayer player)||!stillValid(player))return;
        long tick=player.serverLevel().getGameTime();if(tick<nextDetails)return;nextDetails=tick+5;
        String target=console.schedule.target();if(target.length()>256)target=target.substring(0,256);
        int distance=Double.isFinite(console.schedule.remaining)?(int)Math.clamp(Math.round(Math.max(0,console.schedule.remaining)*10),0,Integer.MAX_VALUE):-1;
        var next=new DriverNetwork.MenuState(containerId,target,distance);
        if(!next.equals(sentDetails)){sentDetails=next;net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,next);}
    }
    @Override public boolean stillValid(Player player){return console==null||DriverControls.accessible(console,player);}
    @Override public boolean clickMenuButton(Player player,int id){if(console==null||!stillValid(player))return false;if(id>=30&&id<=34){boolean result=console.schedule.action(player,id);nextDetails=0;broadcastChanges();return result;}if(id!=35&&(id<3||id>5))return false;console.command(player,id);broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
