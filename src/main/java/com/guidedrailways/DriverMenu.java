package com.guidedrailways;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Only sneak-right settings creates a menu. Driving is independent of menu lifetime. */
public final class DriverMenu extends AbstractContainerMenu {
    private final DriverConsoleEntity console;private final ContainerData data;
    DriverMenu(int id,Inventory inventory,DriverConsoleEntity console){
        super(Content.DRIVER_MENU.get(),id);this.console=console;
        data=console==null?new SimpleContainerData(17):new ContainerData(){
            @Override public int get(int i){return switch(i){case 0->console.active?1:0;case 1->console.direction;case 2->console.notch;case 3->console.emergency?1:0;case 4->(int)Math.clamp(console.actual*100,-32000,32000);case 5->(int)Math.clamp(console.available*100,0,32000);case 6->console.supports;case 7->console.powered;case 8->console.guided;case 9->console.mountBrakes;case 10->1;case 11->inventory.player.getUUID().equals(console.operator)&&DriverControls.primaryView(console)?1:0;case 12->console.schedule.present()?1:0;case 13->DriverControls.primaryView(console)?console.schedule.status:15;case 14->Math.min(console.schedule.total(),console.schedule.entry+1);case 15->console.schedule.total();case 16->DriverControls.primaryView(console)?1:0;default->0;};}
            @Override public void set(int i,int value){}
            @Override public int getCount(){return 17;}
        };addDataSlots(data);
        net.minecraft.world.Container storage=console==null?new net.minecraft.world.SimpleContainer(1):new net.minecraft.world.Container(){
            @Override public int getContainerSize(){return 1;}
            @Override public boolean isEmpty(){return !console.schedule.present();}
            @Override public ItemStack getItem(int slot){return slot==0?console.schedule.item:ItemStack.EMPTY;}
            @Override public ItemStack removeItem(int slot,int count){
                if(slot!=0||count<=0)return ItemStack.EMPTY;
                return console.schedule.removeItem();
            }
            @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
            @Override public void setItem(int slot,ItemStack stack){if(slot==0)console.schedule.setSlot(inventory.player,stack);}
            @Override public void setChanged(){console.setChanged();}
            @Override public boolean stillValid(Player player){return DriverMenu.this.stillValid(player);}
            @Override public void clearContent(){console.schedule.removeItem();}
        };
        addSlot(new Slot(storage,0,268,133){
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean mayPlace(ItemStack stack){return com.simibubi.create.AllItems.SCHEDULE.isIn(stack)&&(console==null||console.schedule.canInsert(inventory.player,stack));}
            @Override public boolean mayPickup(Player player){return console==null||console.schedule.canRemove(player);}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,69+col*18,218+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,69+col*18,276));
    }
    int value(int index){return data.get(index);}
    @Override public boolean stillValid(Player player){return console==null||DriverControls.accessible(console,player);}
    @Override public boolean clickMenuButton(Player player,int id){if(console==null||!stillValid(player))return false;if(id>=30&&id<=32){boolean result=console.schedule.action(player,id);broadcastChanges();return result;}if(id<3||id>5)return false;console.command(player,id);broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem();var original=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(!slots.getFirst().mayPlace(stack)||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return original;
    }
}
