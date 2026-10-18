package com.guidedrailways;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Only sneak-right settings creates a menu. Driving is independent of menu lifetime. */
public final class DriverMenu extends AbstractContainerMenu {
    private final DriverConsoleEntity console;private final ContainerData data;
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console){
        super(Content.DRIVER_MENU.get(),id);this.console=console;
        data=console==null?new SimpleContainerData(16):new ContainerData(){
            @Override public int get(int i){return switch(i){case 0->console.active?1:0;case 1->console.direction;case 2->console.notch;case 3->console.emergency?1:0;case 4->(int)Math.clamp(console.actual*100,-32000,32000);case 5->(int)Math.clamp(console.available*100,0,32000);case 6->console.supports;case 7->console.powered;case 8->console.guided;case 9->console.mountBrakes;case 10->1;case 11->inventory.player.getUUID().equals(console.operator)?1:0;case 12->console.schedule.present()?1:0;case 13->console.schedule.status;case 14->Math.min(console.schedule.total(),console.schedule.entry+1);case 15->console.schedule.total();default->0;};}
            @Override public void set(int i,int value){}
            @Override public int getCount(){return 16;}
        };addDataSlots(data);
    }
    int value(int index){return data.get(index);}
    @Override public boolean stillValid(Player player){return console==null||DriverControls.accessible(console,player);}
    @Override public boolean clickMenuButton(Player player,int id){if(console==null||!stillValid(player))return false;if(id>=30&&id<=32){boolean result=console.schedule.action(player,id);broadcastChanges();return result;}if(id<3||id>5)return false;console.command(player,id);broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
