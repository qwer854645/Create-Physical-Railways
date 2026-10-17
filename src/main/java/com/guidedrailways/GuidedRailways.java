package com.guidedrailways;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;

@Mod(GuidedRailways.ID)
public final class GuidedRailways {
    public static final String ID="guidedrailways";
    public GuidedRailways(IEventBus bus,ModContainer container){
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,TrainConfig.SPEC);
        Content.register(bus);
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre e)->{
            if(e.getLevel() instanceof ServerLevel level){TrackNetwork.refresh(level);VehiclePhysics.tick(level);}
        });
        NeoForge.EVENT_BUS.addListener((dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent e)->VehiclePhysics.step(e.getPhysicsSystem(),e.getTimeStep()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{
            if(e.getLevel() instanceof ServerLevel level){VehiclePhysics.unload(level);TrackNetwork.unload(level);}
        });
    }
}
