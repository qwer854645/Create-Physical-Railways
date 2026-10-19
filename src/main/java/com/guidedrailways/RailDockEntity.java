package com.guidedrailways;
import dev.simulated_team.simulated.content.blocks.docking_connector.*;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.network.chat.Component;
import java.util.*;

final class RailDockEntity extends DockingConnectorBlockEntity implements IHaveGoggleInformation {
    UUID stationId;BlockPos stationPos;final RailDockLease lease=new RailDockLease();
    private boolean trainSide,infoReady;private int bodies;private String stationName="",addresses="";
    private int powerTrace=-1;
    RailDockEntity(BlockPos pos,BlockState state){super(RailDockContent.ENTITY.get(),pos,state);inventory=new RailDockInventory(this);}
    @Override public void initialize(){super.initialize();if(level instanceof ServerLevel){RailDockContent.track(this);refreshBinding();updateInfo();}}
    @Override public void tick(){
        if(level instanceof ServerLevel){RailDockContent.track(this);applyPower();if(level.getGameTime()%20==0){refreshBinding();updateInfo();}}
        super.tick();
        if(level instanceof ServerLevel server&&!powered&&extension.getValue()==0){
            // Native cleanup only runs when the animation target changes. Repair an orphaned
            // head too, including saves loaded with an already-retracted animation target.
            var facing=getBlockState().getValue(DockingConnectorBlock.FACING);var front=worldPosition.relative(facing);
            if(server.hasChunkAt(front)){
                var head=server.getBlockState(front);
                if(head.getBlock() instanceof PairedDockingConnectorBlock&&head.getValue(PairedDockingConnectorBlock.FACING)==facing.getOpposite())
                    server.removeBlock(front,false);
            }
        }
    }
    @Override public void remove(){RailDockContent.untrack(this);super.remove();}
    @Override public void onChunkUnloaded(){RailDockContent.untrack(this);super.onChunkUnloaded();}
    void applyPower(){
        if(!(level instanceof ServerLevel server)||isRemoved()||!server.hasChunkAt(worldPosition))return;
        boolean automatic=lease.active(server.getGameTime()),redstone=server.hasNeighborSignal(worldPosition);
        boolean requested=automatic||redstone;
        int trace=(automatic?1:0)|(redstone?2:0);
        if(powerTrace!=trace){powerTrace=trace;org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[RailDockPower] pos={} automatic={} redstone={} requested={} locked={}",worldPosition,automatic,redstone,requested,isLocked());}
        var state=getBlockState();
        if(state.getValue(DockingConnectorBlock.POWERED)!=requested)server.setBlock(worldPosition,state.setValue(DockingConnectorBlock.POWERED,requested),2);
        if(!requested){
            powered=false;setVirtualLock(false);
            // Do not depend on a surviving entry in the native magnet-pair map. A broken,
            // unloaded or moved peer can leave one endpoint with a stale lock/constraint.
            if(hasOtherConnector()||isLocked()){
                var peer=getOtherConnector();
                if(peer!=null&&peer.getOtherConnector()==this)peer.unDock();
                unDock();
            }
        }
    }
    com.simibubi.create.content.trains.station.GlobalStation linkedStation(){
        if(!(level instanceof ServerLevel server)||stationId==null||stationPos==null||SableCompanion.INSTANCE.getContaining(level,worldPosition)!=null
            ||!server.hasChunkAt(stationPos)||!(server.getBlockEntity(stationPos) instanceof StationBlockEntity station)||station.getStation()==null)return null;
        return stationId.equals(station.getStation().id)?station.getStation():null;
    }
    void refreshBinding(){
        if(!(level instanceof ServerLevel server))return;
        if(SableCompanion.INSTANCE.getContaining(server,worldPosition)!=null){
            RailDockIndex.get(server).bind(worldPosition,null);
            if(stationId!=null){stationId=null;stationPos=null;setChanged();sendData();}
        }else RailDockIndex.get(server).bind(worldPosition,stationId);
    }
    void bind(StationBlockEntity station){
        var target=station.getStation();
        if(Objects.equals(stationId,target.id)){stationId=null;stationPos=null;}
        else{stationId=target.id;stationPos=station.getBlockPos().immutable();}
        setChanged();sendData();refreshBinding();
    }
    Component stateText(){return Component.translatable(isLocked()?"message.guidedrailways.rail_dock.locked":hasOtherConnector()?"message.guidedrailways.rail_dock.pairing":"message.guidedrailways.rail_dock.waiting");}
    void updateInfo(){
        if(!(level instanceof ServerLevel server))return;
        boolean train=SableCompanion.INSTANCE.getContaining(level,worldPosition)!=null,ready=false;int count=0;String name="",mail="";
        if(train){var body=CargoStorage.trainBody(server,worldPosition);if(body!=null){count=CargoStorage.consist(server,body).bodies().size();ready=true;}}
        else if(stationId!=null&&stationPos!=null&&level.hasChunkAt(stationPos)&&level.getBlockEntity(stationPos) instanceof StationBlockEntity station&&station.getStation()!=null&&station.getStation().id.equals(stationId)){
            ready=true;name=station.getStation().name;mail=String.join(" / ",station.getStation().connectedPorts.values().stream().map(p->p.address).distinct().sorted().toList());
        }
        if(trainSide!=train||infoReady!=ready||bodies!=count||!stationName.equals(name)||!addresses.equals(mail)){
            trainSide=train;infoReady=ready;bodies=count;stationName=name;addresses=mail;sendData();
        }
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){
        if(trainSide)tooltip.add(infoReady?Component.translatable("message.guidedrailways.rail_dock.train",bodies):Component.translatable("message.guidedrailways.rail_dock.no_train"));
        else if(infoReady){
            tooltip.add(Component.translatable("message.guidedrailways.rail_dock.station",stationName));
            tooltip.add(addresses.isEmpty()?Component.translatable("message.guidedrailways.rail_dock.no_mailbox"):Component.translatable("message.guidedrailways.rail_dock.addresses",addresses));
        }else tooltip.add(Component.translatable(stationId==null?"message.guidedrailways.rail_dock.unbound":"message.guidedrailways.rail_dock.unready"));
        tooltip.add(stateText());return true;
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean client){
        super.write(tag,registries,client);
        if(stationId!=null&&stationPos!=null){tag.putUUID("RailStation",stationId);tag.putLong("RailStationPos",stationPos.asLong());}
        if(client){tag.putBoolean("RailTrainSide",trainSide);tag.putBoolean("RailInfoReady",infoReady);tag.putInt("RailBodies",bodies);tag.putString("RailStationName",stationName);tag.putString("RailAddresses",addresses);}
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean client){
        super.read(tag,registries,client);stationId=tag.hasUUID("RailStation")?tag.getUUID("RailStation"):null;stationPos=stationId==null?null:BlockPos.of(tag.getLong("RailStationPos"));
        if(client){trainSide=tag.getBoolean("RailTrainSide");infoReady=tag.getBoolean("RailInfoReady");bodies=tag.getInt("RailBodies");stationName=tag.getString("RailStationName");addresses=tag.getString("RailAddresses");}
    }
}
