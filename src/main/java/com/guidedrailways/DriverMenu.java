package com.guidedrailways;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Uses vanilla menu button messages; all edits are checked by the server against the open console. */
public final class DriverMenu extends AbstractContainerMenu {
    private final DriverConsoleEntity console;
    private final ContainerData data;
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console){
        super(Content.DRIVER_MENU.get(),id);this.console=console;
        data=console==null?new SimpleContainerData(10):new ContainerData(){
            @Override public int get(int i){return switch(i){case 0->console.active?1:0;case 1->console.direction;case 2->console.notch;case 3->console.emergency?1:0;case 4->(int)Math.clamp(console.actual*100,-32000,32000);case 5->(int)Math.clamp(console.available*100,0,32000);case 6->console.supports;case 7->console.powered;case 8->console.guided;case 9->console.mountBrakes;default->0;};}
            @Override public void set(int i,int value){}
            @Override public int getCount(){return 10;}
        };addDataSlots(data);
    }
    int value(int index){return data.get(index);}
    @Override public boolean stillValid(Player player){return console==null||DriverControls.accessible(console,player);}
    @Override public boolean clickMenuButton(Player player,int id){if(console==null||!stillValid(player)||id<0||id>18)return false;console.command(player,id);broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
