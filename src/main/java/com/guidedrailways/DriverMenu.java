package com.guidedrailways;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Lightweight control mode and settings share vanilla menu transport and server checks. */
public final class DriverMenu extends AbstractContainerMenu {
    private final DriverConsoleEntity console;private final ContainerData data;private boolean settings;
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console){this(id,inventory,console,false);}
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console,boolean settings){
        super(Content.DRIVER_MENU.get(),id);this.console=console;this.settings=settings;
        data=console==null?new SimpleContainerData(12):new ContainerData(){
            @Override public int get(int i){return switch(i){case 0->console.active?1:0;case 1->console.direction;case 2->console.notch;case 3->console.emergency?1:0;case 4->(int)Math.clamp(console.actual*100,-32000,32000);case 5->(int)Math.clamp(console.available*100,0,32000);case 6->console.supports;case 7->console.powered;case 8->console.guided;case 9->console.mountBrakes;case 10->DriverMenu.this.settings?1:0;case 11->inventory.player.getUUID().equals(console.operator)?1:0;default->0;};}
            @Override public void set(int i,int value){}
            @Override public int getCount(){return 12;}
        };addDataSlots(data);
    }
    int value(int index){return data.get(index);}
    @Override public boolean stillValid(Player player){return console==null||DriverControls.accessible(console,player);}
    @Override public boolean clickMenuButton(Player player,int id){
        if(console==null||!stillValid(player)||id<0||id>24)return false;
        if(id==23){settings=true;console.steer=0;}
        else if(id==24){if(!console.active||!player.getUUID().equals(console.operator))return false;settings=false;}
        else{if(settings&&id!=19&&(id<3||id>5))return false;console.command(player,id);}
        broadcastChanges();return true;
    }
    @Override public void removed(Player player){super.removed(player);if(console!=null&&player.getUUID().equals(console.operator))console.park();}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
