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
import net.neoforged.bus.api.EventPriority;
import net.minecraft.world.level.Level;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlock;
import dev.simulated_team.simulated.content.blocks.docking_connector.PairedDockingConnectorBlock;

final class RailDockInteractions {
    private static final String SELECTED="guidedrailways:rail_dock_selection";
    static void register(){NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,RailDockInteractions::click);}
    private static void click(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        boolean wrench=com.simibubi.create.AllItems.WRENCH.isIn(event.getItemStack());
        if((!wrench&&!event.getItemStack().isEmpty())||(wrench&&event.getEntity().isShiftKeyDown()))return;
        var actor=event.getEntity();var world=event.getLevel();var clicked=event.getPos();
        var endpoint=endpoint(world,clicked);
        // Create opens the station screen locally, before the server can consume the click.
        // Predict only the selection and consumption here; binding stays server-authoritative.
        // Vanilla still sends ServerboundUseItemOnPacket when this local event is canceled.
        if(world.isClientSide){
            if(endpoint!=null){
                if(wrench&&actor.mayBuild()&&SableCompanion.INSTANCE.getContaining(world,endpoint.getBlockPos())==null)
                    select(actor,world,endpoint.getBlockPos());
                consume(event);return;
            }
            if(wrench&&world.getBlockEntity(clicked) instanceof StationBlockEntity&&actor.getPersistentData().contains(SELECTED)){
                actor.getPersistentData().remove(SELECTED);consume(event);
            }
            return;
        }
        if(!(actor instanceof ServerPlayer player))return;
        var level=player.serverLevel();var pos=endpoint==null?clicked:endpoint.getBlockPos();
        var entity=endpoint==null?level.getBlockEntity(pos):endpoint;
        if(entity instanceof RailDockEntity dock){
            if(wrench){
                if(!player.mayBuild()||!level.mayInteract(player,pos))return;
                if(SableCompanion.INSTANCE.getContaining(level,pos)!=null){hint(player,"world_only");}
                else{
                    select(player,level,pos);hint(player,"selected");
                }
            }else{
                dock.updateInfo();var lines=new java.util.ArrayList<Component>();dock.addToGoggleTooltip(lines,false);for(var line:lines)player.displayClientMessage(line,false);
            }
            handled(event,player);return;
        }
        if(!wrench||!(entity instanceof StationBlockEntity station)||!player.getPersistentData().contains(SELECTED))return;
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
    private static RailDockEntity endpoint(Level level,BlockPos pos){
        if(level.getBlockEntity(pos) instanceof RailDockEntity dock)return dock;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof PairedDockingConnectorBlock))return null;
        var facing=state.getValue(PairedDockingConnectorBlock.FACING);
        var base=pos.relative(facing);
        if(!level.hasChunkAt(base)||!(level.getBlockEntity(base) instanceof RailDockEntity dock))return null;
        var baseState=dock.getBlockState();
        return baseState.getValue(DockingConnectorBlock.EXTENDED)&&baseState.getValue(DockingConnectorBlock.FACING)==facing.getOpposite()?dock:null;
    }
    private static void select(net.minecraft.world.entity.player.Player player,Level level,BlockPos pos){
        var selection=new CompoundTag();selection.putLong("Position",pos.asLong());selection.putString("Dimension",level.dimension().location().toString());selection.putLong("Expires",level.getGameTime()+600);
        player.getPersistentData().put(SELECTED,selection);
    }
    private static void consume(PlayerInteractEvent.RightClickBlock event){event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));}
    private static void handled(PlayerInteractEvent.RightClickBlock event,ServerPlayer player){player.swing(event.getHand(),true);consume(event);}
    private static void hint(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("message.guidedrailways.rail_dock."+key),true);}
}
