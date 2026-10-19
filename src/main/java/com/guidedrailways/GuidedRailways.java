package com.guidedrailways;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.trains.track.ITrackBlock;

@Mod(GuidedRailways.ID)
public final class GuidedRailways {
    public static final String ID="guidedrailways";
    public GuidedRailways(IEventBus bus,ModContainer container){
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,TrainConfig.SPEC);
        Content.register(bus);
        if(net.neoforged.fml.ModList.get().isLoaded("simulated"))RailDockContent.register(bus);
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre e)->{
            if(e.getLevel() instanceof ServerLevel level){if(VehiclePhysics.hasVehicles(level))TrackNetwork.refresh(level);VehiclePhysics.tick(level);}
        });
        NeoForge.EVENT_BUS.addListener((dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent e)->{
            VehiclePhysics.step(e.getPhysicsSystem(),e.getTimeStep());
            CouplerConnections.step(e.getPhysicsSystem());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockDropsEvent e)->{
            if(e.getBlockEntity() instanceof DriverConsoleEntity console){
                var item=console.schedule.removeItem();if(item.isEmpty())return;
                var pos=DriverControls.position(console);
                e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(e.getLevel(),pos.x,pos.y,pos.z,item));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{
            if(e.getLevel() instanceof ServerLevel level){VehiclePhysics.unload(level);TrackNetwork.unload(level);CouplerConnections.unload(level);DriverControls.unload(level);CargoStorage.unload(level);}
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->{
            if(!(e.getLevel() instanceof ServerLevel level)||!(e.getEntity() instanceof ServerPlayer player))return;
            if(!AllItems.WRENCH.isIn(player.getItemInHand(e.getHand()))||!VehiclePhysics.hasRelocation(player))return;
            if(VehiclePhysics.owner(level,e.getPos())!=null)return;
            var hit=e.getHitVec();if(hit==null)return;
            if(!VehiclePhysics.relocateClick(player))return;
            player.swing(e.getHand(),true);e.setCanceled(true);e.setCancellationResult(InteractionResult.CONSUME);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e)->{
            if(!(e.getEntity() instanceof ServerPlayer player)||!(e.getLevel() instanceof ServerLevel level))return;
            if(!AllItems.WRENCH.isIn(player.getItemInHand(e.getHand()))||!VehiclePhysics.hasRelocation(player))return;
            if(!VehiclePhysics.relocateClick(player))return;
            player.swing(e.getHand(),true);e.setCanceled(true);e.setCancellationResult(InteractionResult.CONSUME);
        });
    }
}
