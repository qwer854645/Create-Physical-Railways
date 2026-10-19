package com.guidedrailways;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import java.lang.reflect.*;
import java.util.*;

/** Optional public APIs only. Never read a dock's combined shore/ship capability. */
public final class CargoAdapters {
    private static final String DOCK="dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity";
    private record Key(Class<?> type,String name){}
    private static final Map<Key,AccessibleObject> MEMBERS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> WARNED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private CargoAdapters(){}
    static Object call(Object object,String method)throws ReflectiveOperationException{
        var key=new Key(object.getClass(),method);var member=MEMBERS.get(key);
        if(member==null){member=object.getClass().getMethod(method);MEMBERS.put(key,member);}
        return ((Method)member).invoke(object);
    }
    static Object field(Object object,String name)throws ReflectiveOperationException{
        var key=new Key(object.getClass(),name);var member=MEMBERS.get(key);
        if(member==null){member=object.getClass().getField(name);MEMBERS.put(key,member);}
        return ((Field)member).get(object);
    }
    public static boolean dock(BlockEntity entity){
        if(entity==null)return false;
        for(Class<?> type=entity.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals(DOCK))return true;
        return false;
    }
    static boolean hose(BlockEntity entity){return entity.getClass().getName().startsWith("com.mk2525.vsfluidlink.content.");}
    public static void bindConnector(BlockEntity entity){
        try{
            if(dock(entity)){
                CargoTransfers.bind(field(entity,"inventory"),entity);CargoTransfers.bind(field(entity,"tank"),entity);
                var peer=peer(entity);if(peer!=null){CargoTransfers.bind(field(peer,"inventory"),peer);CargoTransfers.bind(field(peer,"tank"),peer);}
            }else if(hose(entity)){localItems(entity);localFluids(entity);}
        }catch(ReflectiveOperationException|RuntimeException error){warn(entity,error);}
    }
    static BlockEntity peer(BlockEntity entity){
        if(!dock(entity))return null;
        try{return (BlockEntity)call(entity,"getOtherConnector");}catch(ReflectiveOperationException error){warn(entity,error);return null;}
    }
    static void warn(Object entity,Exception error){if(WARNED.add(entity.getClass().getName()))org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("Cargo compatibility API unavailable: {}",entity.getClass().getName(),error);}
    public static IItemHandler localItems(BlockEntity entity){
        try{
            if(dock(entity)){
                var inventory=field(entity,"inventory");CargoTransfers.bind(inventory,entity);
                // The public local inventory has one slot; never use getInventory(), which is paired.
                return new IItemHandler(){
                    @Override public int getSlots(){return 1;}
                    @Override public ItemStack getStackInSlot(int slot){try{return (ItemStack)inventory.getClass().getMethod("getItem",int.class).invoke(inventory,slot);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
                    @Override public int getSlotLimit(int slot){try{return (int)call(inventory,"getMaxStackSize");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
                    @Override public ItemStack insertItem(int s,ItemStack stack,boolean simulate){return stack;}
                    @Override public ItemStack extractItem(int s,int amount,boolean simulate){return ItemStack.EMPTY;}
                    @Override public boolean isItemValid(int s,ItemStack stack){return false;}
                };
            }
            if(hose(entity)&&entity.getClass().getSimpleName().startsWith("Item")){
                var storage=(IItemHandler)call(entity,"getInventory");CargoTransfers.bind(storage,entity);return storage;
            }
        }catch(ReflectiveOperationException error){warn(entity,error);throw new IllegalStateException(error);}
        return null;
    }
    public static IFluidHandler localFluids(BlockEntity entity){
        try{
            if(dock(entity)){
                var tank=field(entity,"tank");CargoTransfers.bind(tank,entity);
                return new IFluidHandler(){
                    @Override public int getTanks(){return 1;}
                    @Override public FluidStack getFluidInTank(int index){try{var type=field(tank,"type");long amount=((Number)field(tank,"amount")).longValue();return type==null||amount<=0?FluidStack.EMPTY:new FluidStack((Fluid)field(type,"fluid"),(int)Math.min(Integer.MAX_VALUE,amount));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
                    @Override public int getTankCapacity(int index){try{return ((Number)field(tank,"capacity")).intValue();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
                    @Override public boolean isFluidValid(int i,FluidStack f){return false;}
                    @Override public int fill(FluidStack f,FluidAction a){return 0;}
                    @Override public FluidStack drain(int n,FluidAction a){return FluidStack.EMPTY;}
                    @Override public FluidStack drain(FluidStack f,FluidAction a){return FluidStack.EMPTY;}
                };
            }
            if(hose(entity)&&!entity.getClass().getSimpleName().startsWith("Item")){
                try{var storage=(IFluidHandler)call(entity,"getTank");CargoTransfers.bind(storage,entity);return storage;}catch(NoSuchMethodException ignored){}
            }
        }catch(ReflectiveOperationException error){warn(entity,error);throw new IllegalStateException(error);}
        return null;
    }
}
