package com.guidedrailways;
import dev.ryanhcode.sable.companion.SableCompanion;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class RailDockInteractions {
    private static final String SELECTED="guidedrailways:rail_dock_selection";
    static void register(){NeoForge.EVENT_BUS.addListener(RailDockInteractions::click);}
    private static void click(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().isEmpty())return;
        var level=player.serverLevel();var pos=event.getPos();var entity=level.getBlockEntity(pos);
        if(entity instanceof RailDockEntity dock){
            if(player.isShiftKeyDown()){
                if(!player.mayBuild()||!level.mayInteract(player,pos))return;
                if(SableCompanion.INSTANCE.getContaining(level,pos)!=null){hint(player,"world_only");}
                else{
                    var selection=new CompoundTag();selection.putLong("Position",pos.asLong());selection.putString("Dimension",level.dimension().location().toString());selection.putLong("Expires",level.getGameTime()+600);
                    player.getPersistentData().put(SELECTED,selection);hint(player,"selected");
                }
            }else{
                dock.updateInfo();var lines=new java.util.ArrayList<Component>();dock.addToGoggleTooltip(lines,false);for(var line:lines)player.displayClientMessage(line,false);
            }
            handled(event,player);return;
        }
        if(!(entity instanceof StationBlockEntity station)||!player.getPersistentData().contains(SELECTED))return;
        var selection=player.getPersistentData().getCompound(SELECTED);player.getPersistentData().remove(SELECTED);
        var chosen=BlockPos.of(selection.getLong("Position"));
        if(!player.mayBuild()||!level.mayInteract(player,pos)||!selection.getString("Dimension").equals(level.dimension().location().toString())
            ||level.getGameTime()>selection.getLong("Expires")||pos.distSqr(chosen)>4096||!level.hasChunkAt(chosen)||!level.mayInteract(player,chosen)
            ||!(level.getBlockEntity(chosen) instanceof RailDockEntity dock)||SableCompanion.INSTANCE.getContaining(level,chosen)!=null){hint(player,"expired");handled(event,player);return;}
        if(station.getStation()==null){hint(player,"unready");handled(event,player);return;}
        dock.lease.clear(level.getGameTime());dock.bind(station);dock.applyPower();dock.updateInfo();
        if(dock.stationId==null)hint(player,"cleared");
        else player.displayClientMessage(Component.translatable("message.guidedrailways.rail_dock.bound",station.getStation().name),true);
        handled(event,player);
    }
    private static void handled(PlayerInteractEvent.RightClickBlock event,ServerPlayer player){player.swing(event.getHand(),true);event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);}
    private static void hint(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("message.guidedrailways.rail_dock."+key),true);}
}
