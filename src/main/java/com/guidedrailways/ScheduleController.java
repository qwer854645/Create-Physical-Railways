package com.guidedrailways;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.schedule.condition.*;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** An original runtime for Create schedule DATA; never ticks a fake Create Train. */
final class ScheduleController {
    static final int EMPTY=0,PAUSED=1,PLANNING=2,TRAVELLING=3,WAITING=4,FINISHED=5,UNSUPPORTED=6,
        NO_ROUTE=7,NO_POWER=8,TRACK_MISSING=9,CONFLICT=10,CHANGED=11,OVERSHOOT=12,UNLOADED=13,EMERGENCY=14,SIGNAL_WAIT=16,CARGO_CONNECTOR=17,DOCK_WAIT=18,PACKAGE_TARGET=19,PACKAGE_WAIT=20,RAIL_DOCK=21;
    final DriverConsoleEntity owner;ItemStack item=ItemStack.EMPTY;Schedule program;
    boolean running,manualOverride,waiting,releaseBrakes;long nextPlan,nextVisual;boolean visualDirty;int entry,status,stableTicks;UUID station;
    AutomaticTrains.Plan plan;Set<UUID> members;DriverConsoleEntity manualSource;
    volatile boolean routeRetry;
    boolean resumePending;private ScheduleReloadGate reloadGate;
    double remaining=Double.NaN;long nextMail;int mailState=WAITING,conditionState=WAITING;boolean mailMonitored;
    double throttle=1;String currentTitle="";
    Set<UUID> cargoBodies;
    final List<Integer> progress=new ArrayList<>();final List<CompoundTag> contexts=new ArrayList<>();
    ScheduleController(DriverConsoleEntity owner){this.owner=owner;}
    boolean present(){return !item.isEmpty();}
    int total(){var p=program();return p==null?0:p.entries.size();}
    Schedule program(){
        if(program==null&&present()&&owner.getLevel()!=null)try{program=ScheduleItem.getSchedule(owner.getLevel().registryAccess(),item);}catch(RuntimeException ignored){}
        return program;
    }
    private static String unsupported(Schedule schedule){
        if(schedule==null||schedule.entries.isEmpty())return "empty";
        for(var entry:schedule.entries){
            if(entry==null||entry.instruction==null||entry.conditions==null)return "invalid";
            var type=entry.instruction.getClass();
            if((type==FetchPackagesInstruction.class||type==DeliverPackagesInstruction.class)&&!RailDocks.available())return "simulated";
            if(!travelInstruction(entry.instruction)&&type!=ChangeThrottleInstruction.class&&type!=ChangeTitleInstruction.class)return entry.instruction.getId().toString();
            if(!travelInstruction(entry.instruction))continue;
            for(var branch:entry.conditions)for(var c:branch)
                if(!supportedCondition(c))return c.getId().toString();
        }
        return null;
    }
    private static boolean supportedCondition(ScheduleWaitCondition c){
        return c!=null&&(c.getClass()==ScheduledDelay.class||c.getClass()==TimeOfDayCondition.class
            ||c.getClass()==StationPoweredCondition.class||c.getClass()==RedstoneLinkCondition.class
            ||c.getClass()==ItemThresholdCondition.class||c.getClass()==FluidThresholdCondition.class||c.getClass()==IdleCargoCondition.class
            ||c.getClass()==PlayerPassengerCondition.class);
    }
    private void resetSettings(){throttle=1;currentTitle="";cargoBodies=null;}
    boolean prepareInstructions(){
        var p=program();int previousEntry=entry;double previousThrottle=throttle;String previousTitle=currentTitle;
        var result=SchedulePrelude.run(p.entries.size(),p.cyclic,entry,32,
            i->travelInstruction(p.entries.get(i).instruction),
            i->{
                var instruction=p.entries.get(i).instruction;
                if(instruction.getClass()==ChangeThrottleInstruction.class)throttle=SchedulePrelude.throttle(((ChangeThrottleInstruction)instruction).getThrottle());
                else if(instruction.getClass()==ChangeTitleInstruction.class)currentTitle=((ChangeTitleInstruction)instruction).getScheduleTitle();
                else throw new IllegalStateException("Unsupported non-travel instruction "+instruction.getId());
            });
        if(result.executed()>0){
            entry=result.entry();waiting=false;station=null;plan=null;remaining=Double.NaN;stableTicks=0;nextPlan=0;resetConditions();
            if(entry!=previousEntry||throttle!=previousThrottle||!currentTitle.equals(previousTitle)){
                syncProgress();
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[ScheduleSettings] console={} entry={} throttle={} title={}",owner.getBlockPos(),entry,throttle,currentTitle);
            }
        }
        if(result.finished()){running=false;state(FINISHED);return false;}
        if(instruction()==null){state(PLANNING);return false;}
        return true;
    }
    boolean install(Player player,ItemStack held){
        if(!canInsert(player,held))return false;
        var stored=held.copyWithCount(1);
        if(present())give(player);
        if(!player.getAbilities().instabuild)held.shrink(1);
        store(player,stored);return true;
    }
    boolean canInsert(Player player,ItemStack held){
        if(!DriverControls.isPrimary(owner)){DriverControls.secondaryHint(player);return false;}
        if(!allowed(player)){hint(player,"unavailable");return false;}
        if(!DriverControls.stopped(owner)){hint(player,"stop_first");return false;}
        Schedule parsed;
        try{parsed=ScheduleItem.getSchedule(owner.getLevel().registryAccess(),held);}
        catch(RuntimeException error){hint(player,"invalid");return false;}
        String unsupported=unsupported(parsed);
        if(unsupported!=null){player.displayClientMessage(Component.translatable("gui.guidedrailways.auto.unsupported_item",unsupported),true);return false;}
        return true;
    }
    private void store(Player player,ItemStack stack){
        item=stack.copyWithCount(1);program=null;var parsed=program();
        entry=Math.clamp(parsed.savedProgress,0,parsed.entries.size()-1);waiting=false;station=null;resetConditions();resetSettings();members=null;manualOverride=false;manualSource=null;
        running=false;plan=null;state(PAUSED);owner.setChanged();
        // Store the actual schedule first; installation uses the same validated start path as resume.
        if(action(player,30))hint(player,"installed");
    }
    private boolean allowed(Player player){
        return owner.getLevel() instanceof ServerLevel level&&player.mayBuild()&&level.mayInteract(player,owner.getBlockPos())
            &&DriverControls.accessible(owner,player)&&(!owner.active||player.getUUID().equals(owner.operator));
    }
    boolean action(Player player,int id){
        if(!allowed(player))return false;
        if(id!=32&&!DriverControls.isPrimary(owner)){DriverControls.secondaryHint(player);return false;}
        if(id==31){pause(PAUSED);owner.park();hint(player,"paused");return true;}
        if(id==32){if(!DriverControls.stopped(owner)){hint(player,"stop_first");return false;}give(player);return true;}
        if((id==33||id==34)&&present()){
            if(!DriverControls.stopped(owner)){hint(player,"stop_first");return false;}
            if(unsupported(program())!=null){hint(player,"invalid");return false;}
            if(id==34&&owner.emergency){hint(player,"emergency");return false;}
            if(id==33&&entry>=total())return false;
            owner.park();
            if(id==33){advance();if(entry<total())pause(PAUSED);hint(player,"skipped");return true;}
            entry=0;waiting=false;station=null;resetConditions();resetSettings();syncProgress();
            return action(player,30);
        }
        if(id!=30||!present())return false;
        var p=program();String error=unsupported(p);if(error!=null){state(UNSUPPORTED);hint(player,"invalid");return false;}
        if(owner.emergency){hint(player,"emergency");return false;}
        if(DriverControls.body(owner)==null){hint(player,"install_body");return false;}
        if(entry>=p.entries.size()){entry=0;waiting=false;station=null;resetConditions();resetSettings();}
        owner.leave();owner.enabled=false;running=true;routeRetry=false;resumePending=false;reloadGate=null;manualOverride=false;manualSource=null;plan=null;remaining=Double.NaN;members=null;cargoBodies=null;stableTicks=0;nextPlan=0;releaseBrakes=true;syncProgress();state(PLANNING);return true;
    }
    void manual(DriverConsoleEntity source){
        RailDocks.release(this);
        running=false;routeRetry=false;resumePending=false;reloadGate=null;manualOverride=true;manualSource=source;plan=null;remaining=Double.NaN;stableTicks=0;state(PAUSED);
    }
    void pause(int reason){RailDocks.release(this);running=false;routeRetry=false;resumePending=false;reloadGate=null;manualOverride=false;manualSource=null;plan=null;remaining=Double.NaN;stableTicks=0;state(reason);}
    boolean restore(ServerLevel level,Set<UUID> actual){
        if(!resumePending)return true;
        var readiness=reloadGate.tick(actual,level.getGameTime());
        if(readiness==ScheduleReloadGate.Result.WAIT)return false;
        if(readiness==ScheduleReloadGate.Result.CHANGED){pause(CHANGED);return false;}
        var parsed=program();
        if(unsupported(parsed)!=null){pause(UNSUPPORTED);return false;}
        entry=Math.min(entry,parsed.entries.size());
        if(entry>=parsed.entries.size()){resumePending=false;reloadGate=null;running=false;state(FINISHED);return false;}
        resumePending=false;reloadGate=null;running=true;members=Set.copyOf(actual);releaseBrakes=true;nextPlan=0;syncProgress();state(PLANNING);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[ScheduleRestored] console={} entry={} waiting={} conditions={} bogies={}",owner.getBlockPos(),entry,waiting,progress,actual.size());
        return true;
    }
    void visual(int notch,int direction){
        if(owner.notch!=notch||owner.direction!=direction){owner.notch=notch;owner.direction=direction;visualDirty=true;owner.setChanged();}
        if(visualDirty&&owner.getLevel()!=null&&owner.getLevel().getGameTime()>=nextVisual){visualDirty=false;nextVisual=owner.getLevel().getGameTime()+5;owner.updateVisual();}
    }
    void state(int next){
        if(status==next)return;status=next;owner.setChanged();
        if(owner.getLevel() instanceof ServerLevel level)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[AutomaticTrain] console={} state={} entry={} target={}",owner.getBlockPos(),next,entry+1,station);
    }
    private void syncProgress(){
        if(!present())return;
        var p=program();if(p==null||p.entries.isEmpty()||owner.getLevel()==null)return;
        p.savedProgress=Math.clamp(entry,0,p.entries.size()-1);
        item.set(com.simibubi.create.AllDataComponents.TRAIN_SCHEDULE,p.write(owner.getLevel().registryAccess()));
        owner.setChanged();
    }
    void give(Player player){if(present())player.getInventory().placeItemBackInInventory(removeItem());}
    ItemStack removeItem(){
        syncProgress();var stack=item;owner.park();item=ItemStack.EMPTY;program=null;entry=0;waiting=false;station=null;members=null;releaseBrakes=false;resetConditions();resetSettings();pause(EMPTY);owner.setChanged();return stack;
    }
    String target(){
        if(!present()||entry>=total())return "";
        if(plan!=null)return plan.route.goal().name();
        var instruction=instruction();
        if(instruction instanceof DestinationInstruction destination)return destination.getFilter();
        return instruction==null?"":instruction.getSummary().getSecond().getString();
    }
    private static boolean travelInstruction(ScheduleInstruction instruction){return instruction!=null&&(instruction.getClass()==DestinationInstruction.class||instruction.getClass()==FetchPackagesInstruction.class||instruction.getClass()==DeliverPackagesInstruction.class);}
    ScheduleInstruction instruction(){var p=program();return p!=null&&entry>=0&&entry<p.entries.size()&&travelInstruction(p.entries.get(entry).instruction)?p.entries.get(entry).instruction:null;}
    boolean packageInstruction(){return instruction() instanceof FetchPackagesInstruction||instruction() instanceof DeliverPackagesInstruction;}
    void resetConditions(){progress.clear();contexts.clear();nextMail=0;mailState=WAITING;mailMonitored=false;routeRetry=false;}
    boolean exchangeMail(ServerLevel level){
        if(level.getGameTime()>=nextMail){nextMail=level.getGameTime()+20;mailState=PackageMail.exchange(level,this);}
        if(mailState!=WAITING){state(mailState);return false;}
        return true;
    }
    boolean tickConditions(ServerLevel level){
        conditionState=WAITING;
        var branches=program().entries.get(entry).conditions;if(branches.isEmpty())return true;
        if(progress.size()!=branches.size()){progress.clear();contexts.clear();for(var b:branches){progress.add(0);contexts.add(new CompoundTag());}}
        for(int i=0;i<branches.size();i++){
            var branch=branches.get(i);int index=progress.get(i);if(index>=branch.size())return true;
            var c=branch.get(index);
            if(!supportedCondition(c)){pause(UNSUPPORTED);return false;}
            if(conditionComplete(level,c,contexts.get(i))){
                progress.set(i,index+1);contexts.set(i,new CompoundTag());owner.setChanged();
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[ScheduleCondition] console={} entry={} branch={} condition={} completed=true",owner.getBlockPos(),entry,i,c.getId());
                if(index+1>=branch.size())return true;
            }
        }
        state(conditionState);if(level.getGameTime()%20==0)owner.setChanged();return false;
    }
    private boolean conditionComplete(ServerLevel level,ScheduleWaitCondition condition,CompoundTag context){
        if(condition.getClass()==PlayerPassengerCondition.class){
            var passengers=TrainPassengers.snapshot(level,DriverControls.body(owner));
            if(!passengers.complete()||cargoBodies!=null&&!cargoBodies.equals(passengers.bodies())){conditionState=UNLOADED;return false;}
            if(cargoBodies==null){cargoBodies=passengers.bodies();owner.setChanged();}
            var count=(PlayerPassengerCondition)condition;
            if(!context.contains("PrevPlayerCount")||context.getInt("PrevPlayerCount")!=passengers.count()){
                context.putInt("PrevPlayerCount",passengers.count());owner.setChanged();
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[PassengerWait] console={} entry={} seated={} target={} atLeast={} bodies={}",owner.getBlockPos(),entry,passengers.count(),count.getTarget(),count.canOvershoot(),passengers.bodies().size());
            }
            return passengers.satisfies(count.getTarget(),count.canOvershoot());
        }
        if(condition.getClass()==ItemThresholdCondition.class||condition.getClass()==FluidThresholdCondition.class||condition.getClass()==IdleCargoCondition.class){
            var snapshot=CargoStorage.snapshot(level,DriverControls.body(owner));
            if(!snapshot.complete()||cargoBodies!=null&&!cargoBodies.equals(snapshot.bodies())){conditionState=UNLOADED;context.remove("CargoWaitStarted");return false;}
            if(cargoBodies==null){cargoBodies=snapshot.bodies();owner.setChanged();}
            if(condition.getClass()==IdleCargoCondition.class){
                if(!snapshot.monitored()&&!mailMonitored){conditionState=CARGO_CONNECTOR;context.remove("CargoWaitStarted");return false;}
                if(!context.contains("CargoWaitStarted"))context.putLong("CargoWaitStarted",level.getGameTime());
                return CargoStorage.idle(level,snapshot,context.getLong("CargoWaitStarted"),((IdleCargoCondition)condition).totalWaitTicks(),mailMonitored);
            }
            if(context.contains("CargoNextCheck")&&level.getGameTime()<context.getLong("CargoNextCheck"))return false;
            context.putLong("CargoNextCheck",level.getGameTime()+20);
            var threshold=(CargoThresholdCondition)condition;
            var filter=com.simibubi.create.content.logistics.filter.FilterItemStack.of(condition.getItem(0));
            boolean item=condition.getClass()==ItemThresholdCondition.class;
            long amount=item?snapshot.items(level,filter,threshold.getMeasure()==1):snapshot.fluids(level,filter);
            long target=(long)threshold.getThreshold()*(item?1:1000);
            if(!context.contains("CargoAmount")||context.getLong("CargoAmount")!=amount)
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[CargoWait] console={} entry={} type={} amount={} operator={} target={} bodies={} storageRoots={}",owner.getBlockPos(),entry,condition.getId(),amount,threshold.getOperator(),target,snapshot.bodies().size(),snapshot.locations().size());
            context.putLong("CargoAmount",amount);
            return CargoClock.compare(amount,target,threshold.getOperator().name());
        }
        if(condition.getClass()==StationPoweredCondition.class){
            if(station==null)return false;
            for(var graph:com.simibubi.create.Create.RAILWAYS.sided(level).trackNetworks.values()){
                var stop=graph.getPoint(com.simibubi.create.content.trains.graph.EdgePointType.STATION,station);if(stop==null)continue;
                var world=level.getServer().getLevel(stop.getBlockEntityDimension());var pos=stop.getBlockEntityPos();
                return world!=null&&pos!=null&&world.hasChunkAt(pos)
                    &&world.getBlockEntity(pos) instanceof com.simibubi.create.content.trains.station.StationBlockEntity&&world.hasNeighborSignal(pos);
            }
            return false;
        }
        if(condition.getClass()==RedstoneLinkCondition.class){
            var link=(RedstoneLinkCondition)condition;
            // Same global frequency semantics as Create, without its persisted version cache:
            // a version number from a previous server run must not suppress the first check.
            return com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(link.freq)!=link.lowActivation();
        }
        // Only the two exact time-condition classes reach this null-Train API call.
        return condition.tickCompletion(level,null,context);
    }
    void advance(){
        RailDocks.release(this);
        nextPlan=0;entry++;waiting=false;station=null;plan=null;remaining=Double.NaN;stableTicks=0;resetConditions();
        if(entry>=program().entries.size()){
            if(program().cyclic)entry=0;else{running=false;syncProgress();state(FINISHED);return;}
        }
        syncProgress();state(PLANNING);owner.setChanged();
    }
    private static void hint(Player player,String key){player.displayClientMessage(Component.translatable("gui.guidedrailways.auto."+key),true);}
    void save(CompoundTag tag,HolderLookup.Provider registries){
        if(!present())return;
        var data=new CompoundTag();data.put("Item",item.save(registries));data.putInt("Entry",entry);data.putBoolean("Waiting",waiting);
        data.putInt("Version",1);data.putBoolean("Running",running||resumePending);data.putInt("Status",status);
        data.putDouble("Throttle",throttle);data.putString("Title",currentTitle);
        var savedMembers=new ListTag();if(members!=null)for(var id:members)savedMembers.add(StringTag.valueOf(id.toString()));data.put("Members",savedMembers);
        var cargo=new ListTag();if(cargoBodies!=null)for(var id:cargoBodies)cargo.add(StringTag.valueOf(id.toString()));data.put("CargoBodies",cargo);
        if(station!=null)data.putUUID("Station",station);
        var list=new ListTag();for(int i=0;i<progress.size();i++){var branch=new CompoundTag();branch.putInt("Progress",progress.get(i));branch.put("Context",contexts.get(i).copy());list.add(branch);}data.put("Conditions",list);
        tag.put("PhysicalSchedule",data);
    }
    void load(CompoundTag tag,HolderLookup.Provider registries){
        var data=tag.getCompound("PhysicalSchedule");item=ItemStack.parseOptional(registries,data.getCompound("Item"));program=null;
        throttle=SchedulePrelude.throttle(data.getDouble("Throttle"));currentTitle=data.getString("Title");
        entry=Math.max(0,data.getInt("Entry"));waiting=data.getBoolean("Waiting");station=data.hasUUID("Station")?data.getUUID("Station"):null;
        resetConditions();var list=data.getList("Conditions",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var b=list.getCompound(i);progress.add(Math.max(0,b.getInt("Progress")));var context=b.getCompound("Context").copy();context.remove("CargoNextCheck");context.remove("CargoWaitStarted");contexts.add(context);}
        var cargo=new HashSet<UUID>();var cargoList=data.getList("CargoBodies",Tag.TAG_STRING);for(int i=0;i<cargoList.size();i++)try{cargo.add(UUID.fromString(cargoList.getString(i)));}catch(IllegalArgumentException ignored){}cargoBodies=cargo.isEmpty()?null:Set.copyOf(cargo);
        var savedMembers=data.getList("Members",Tag.TAG_STRING);var restoredMembers=new HashSet<UUID>();
        for(int i=0;i<savedMembers.size();i++)try{restoredMembers.add(UUID.fromString(savedMembers.getString(i)));}catch(IllegalArgumentException ignored){}
        members=restoredMembers.isEmpty()?null:Set.copyOf(restoredMembers);
        resumePending=present()&&data.getBoolean("Running");reloadGate=resumePending?new ScheduleReloadGate(members):null;
        running=false;manualOverride=false;manualSource=null;plan=null;stableTicks=0;remaining=Double.NaN;nextPlan=nextVisual=0;visualDirty=false;releaseBrakes=false;
        status=!present()?EMPTY:resumePending?UNLOADED:data.getInt("Status")==FINISHED?FINISHED:PAUSED;
    }
}
