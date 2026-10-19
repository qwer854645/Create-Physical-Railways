package com.guidedrailways;
import dev.simulated_team.simulated.content.blocks.docking_connector.*;
import dev.simulated_team.simulated.multiloader.inventory.neoforge.ContainerWrapper;
import dev.simulated_team.simulated.multiloader.tanks.neoforge.SingleTankWrapper;
import dev.simulated_team.simulated.multiloader.energy.SingleBatteryWrapper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.capabilities.*;
import java.util.*;
import org.joml.Vector3d;

/** Loaded only when the optional Simulated mod is present. */
final class RailDockContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(GuidedRailways.ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(GuidedRailways.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,GuidedRailways.ID);
    static final DeferredBlock<RailDockBlock> BLOCK=BLOCKS.register("rail_docking_connector",()->new RailDockBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(dev.simulated_team.simulated.index.SimBlocks.DOCKING_CONNECTOR.get())));
    static final DeferredHolder<BlockEntityType<?>,BlockEntityType<DockingConnectorBlockEntity>> ENTITY=ENTITIES.register("rail_docking_connector",()->BlockEntityType.Builder.<DockingConnectorBlockEntity>of(RailDockEntity::new,BLOCK.get()).build(null));
    static final DeferredItem<BlockItem> ITEM=ITEMS.register("rail_docking_connector",()->new BlockItem(BLOCK.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){UserText.tooltip(tooltip,"tooltip.guidedrailways.rail_docking_connector");}
    });
    private static final Map<ServerLevel,Map<BlockPos,RailDockEntity>> LIVE=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,String>> TRACE=new WeakHashMap<>();
    static void track(RailDockEntity entity){if(entity.getLevel() instanceof ServerLevel level)LIVE.computeIfAbsent(level,k->new HashMap<>()).put(entity.getBlockPos().immutable(),entity);}
    static void untrack(RailDockEntity entity){if(entity.getLevel() instanceof ServerLevel level){var entries=LIVE.get(level);if(entries!=null)entries.remove(entity.getBlockPos(),entity);}}
    static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);bus.addListener(RailDockContent::capabilities);RailDockInteractions.register();
        if(net.neoforged.fml.ModList.get().isLoaded("computercraft"))registerWired();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel server){LIVE.remove(server);TRACE.remove(server);}});
        RailDocks.bridge=new RailDocks.Bridge(){
            public ItemStack item(){return new ItemStack(ITEM.get());}
            public boolean allowsHandoff(net.minecraft.world.level.block.entity.BlockEntity source,net.minecraft.world.level.block.entity.BlockEntity destination){return !(source instanceof DockingConnectorBlockEntity dock)||RailDockContent.allowsTransfer(source,destination,dock.inventory.getItem(0));}
            public boolean allowsBuffers(Object source,Object destination,ItemStack stack){
                if(!(source instanceof RailDockInventory)&&!(destination instanceof RailDockInventory))return true;
                DockingConnectorBlockEntity from,to;
                if(source instanceof RailDockInventory buffer){from=buffer.owner;to=from.getOtherConnector();}
                else{to=((RailDockInventory)destination).owner;from=to.getOtherConnector();}
                return from!=null&&to!=null&&from.inventory==source&&to.inventory==destination&&RailDockContent.allowsTransfer(from,to,stack);
            }
            public int acquire(ServerLevel level,ScheduleController controller,CargoStorage.Snapshot cargo){
                int state=RailDockContent.acquire(level,controller,cargo);String value=controller.station+":"+state;
                var traces=TRACE.computeIfAbsent(level,k->new HashMap<>());
                if(!value.equals(traces.put(controller.owner.consoleId,value)))org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[RailDock] console={} station={} state={} bodies={}",controller.owner.getBlockPos(),controller.station,state,cargo.bodies().size());
                return state;
            }
            public void release(ServerLevel level,UUID console){
                var traces=TRACE.get(level);if(traces!=null)traces.remove(console);
                for(var dock:List.copyOf(LIVE.getOrDefault(level,Map.of()).values()))if(!dock.isRemoved()&&level.hasChunkAt(dock.getBlockPos())&&dock.lease.release(console,level.getGameTime()))dock.applyPower();
            }
        };
        if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)RailDockClient.register(bus);
    }
    private static void registerWired(){
        try{
            var service=dev.simulated_team.simulated.service.ServiceUtil.load(dev.simulated_team.simulated.service.compat.SimPeripheralService.class);
            var getter=Class.forName("dev.simulated_team.simulated.service.compat.SimPeripheralService$CapabilityGetter");
            var proxy=java.lang.reflect.Proxy.newProxyInstance(getter.getClassLoader(),new Class<?>[]{getter},(object,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "hashCode"->System.identityHashCode(object);case "equals"->object==args[0];default->"Railway wired endpoint";};
                var dock=(DockingConnectorBlockEntity)args[0];return args[1]==dock.getBlockState().getValue(DockingConnectorBlock.FACING)?null:dock.ccWiredElement;
            });
            dev.simulated_team.simulated.service.compat.SimPeripheralService.class.getMethod("addWired",java.util.function.Supplier.class,getter).invoke(service,ENTITY,proxy);
        }catch(ReflectiveOperationException error){org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).warn("Railway connector wired capability unavailable",error);}
    }
    private static void capabilities(RegisterCapabilitiesEvent event){
        // A capability can outlive a docking session. Resolve the native pair on every call
        // rather than retaining the former peer's buffers after undocking or redocking.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENTITY.get(),(dock,side)->new net.neoforged.neoforge.items.IItemHandler(){
            private ContainerWrapper<?> current(){return new ContainerWrapper<>(dock.getInventory());}
            public int getSlots(){return current().getSlots();}
            public ItemStack getStackInSlot(int slot){return current().getStackInSlot(slot);}
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
                if(!RailDockContent.allowsTransfer(dock,dock.getOtherConnector(),stack))return stack;
                return current().insertItem(slot,stack,simulate);
            }
            public ItemStack extractItem(int slot,int amount,boolean simulate){return current().extractItem(slot,amount,simulate);}
            public int getSlotLimit(int slot){return current().getSlotLimit(slot);}
            public boolean isItemValid(int slot,ItemStack stack){
                var peer=dock.getOtherConnector();
                return slot==0&&peer!=null&&RailDockContent.allowsTransfer(dock,peer,stack);
            }
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,ENTITY.get(),(dock,side)->new SingleTankWrapper(dock.tank));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,ENTITY.get(),(dock,side)->new SingleBatteryWrapper(dock.battery));
    }
    static boolean allowsTransfer(net.minecraft.world.level.block.entity.BlockEntity source,net.minecraft.world.level.block.entity.BlockEntity destination,ItemStack stack){
        if(!(source instanceof RailDockEntity)&&!(destination instanceof RailDockEntity))return true;
        if(!com.simibubi.create.content.logistics.box.PackageItem.isPackage(stack))return true;
        if(!CargoHookStatus.ready("dock"))return false;
        if(!(source instanceof DockingConnectorBlockEntity from)||!(destination instanceof DockingConnectorBlockEntity to)
            ||!(from.getLevel() instanceof ServerLevel level)||to.getLevel()!=level)return false;
        boolean receivingWorld=SableCompanion.INSTANCE.getContaining(level,to.getBlockPos())==null;
        var shore=receivingWorld?to:from;var car=receivingWorld?from:to;
        var station=shore instanceof RailDockEntity dock?dock.linkedStation():null;
        boolean ready=station!=null&&!station.connectedPorts.isEmpty()&&CargoStorage.trainBody(level,car.getBlockPos())!=null;
        boolean matches=ready&&station.connectedPorts.values().stream().anyMatch(port->com.simibubi.create.content.logistics.box.PackageItem.matchAddress(stack,port.address));
        boolean paired=from.getOtherConnector()==to&&to.getOtherConnector()==from&&from.isLocked()&&to.isLocked();
        return RailDockMail.allows(true,paired,ready,receivingWorld,matches);
    }
    private static Vector3d position(RailDockEntity dock){
        var p=dock.getBlockPos();var local=new Vector3d(p.getX()+.5,p.getY()+.5,p.getZ()+.5);
        return SableCompanion.INSTANCE.getContaining(dock.getLevel(),p) instanceof ServerSubLevel body?VehiclePhysics.world(body,local):local;
    }
    private static int acquire(ServerLevel level,ScheduleController controller,CargoStorage.Snapshot cargo){
        if(!CargoHookStatus.ready("dock"))return ScheduleController.RAIL_DOCK;
        var index=RailDockIndex.get(level);var ends=index.endpoints(controller.station);
        if(ends.isEmpty())return ScheduleController.RAIL_DOCK;
        var world=new ArrayList<RailDockEntity>();boolean unloaded=false;
        for(var pos:ends){
            if(!level.hasChunkAt(pos)){unloaded=true;continue;}
            if(!(level.getBlockEntity(pos) instanceof RailDockEntity dock)||!controller.station.equals(dock.stationId)||SableCompanion.INSTANCE.getContaining(level,pos)!=null){index.bind(pos,null);continue;}
            if(dock.stationPos==null||!level.hasChunkAt(dock.stationPos)){unloaded=true;continue;}
            if(!(level.getBlockEntity(dock.stationPos) instanceof com.simibubi.create.content.trains.station.StationBlockEntity station)||station.getStation()==null||!station.getStation().id.equals(controller.station))continue;
            world.add(dock);
        }
        var cars=new ArrayList<RailDockEntity>();
        for(var location:cargo.locations())if(location.dock()&&level.hasChunkAt(location.pos())&&level.getBlockEntity(location.pos()) instanceof RailDockEntity dock
            &&SableCompanion.INSTANCE.getContaining(level,location.pos()) instanceof ServerSubLevel body&&cargo.bodies().contains(body.getUniqueId()))cars.add(dock);
        RailDockEntity chosenWorld=null,chosenCar=null;double best=Double.POSITIVE_INFINITY;long now=level.getGameTime();var console=controller.owner.consoleId;
        for(var a:world)for(var b:cars){
            if(!a.lease.canClaim(console,now)||!b.lease.canClaim(console,now))continue;
            if(a.getOtherConnector()!=null&&a.getOtherConnector()!=b||b.getOtherConnector()!=null&&b.getOtherConnector()!=a)continue;
            double d=position(a).distanceSquared(position(b));if(d>256)continue;
            if(a.getOtherConnector()==b&&b.getOtherConnector()==a)d=-1;
            if(d<best){best=d;chosenWorld=a;chosenCar=b;}
        }
        if(chosenWorld==null)return world.isEmpty()&&unloaded?ScheduleController.UNLOADED:ScheduleController.RAIL_DOCK;
        chosenWorld.lease.claim(console,now);chosenCar.lease.claim(console,now);chosenWorld.applyPower();chosenCar.applyPower();
        var body=SableCompanion.INSTANCE.getContaining(level,chosenCar.getBlockPos());
        return RailDockGate.ready(controller.station,chosenWorld.stationId,body==null?null:body.getUniqueId(),cargo.bodies(),chosenWorld.getOtherConnector()==chosenCar&&chosenCar.getOtherConnector()==chosenWorld,chosenWorld.isLocked(),chosenCar.isLocked())?ScheduleController.WAITING:ScheduleController.RAIL_DOCK;
    }
}
