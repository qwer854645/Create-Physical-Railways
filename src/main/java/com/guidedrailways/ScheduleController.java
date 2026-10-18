package com.guidedrailways;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
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
        NO_ROUTE=7,NO_POWER=8,TRACK_MISSING=9,CONFLICT=10,CHANGED=11,OVERSHOOT=12,UNLOADED=13,EMERGENCY=14;
    final DriverConsoleEntity owner;ItemStack item=ItemStack.EMPTY;Schedule program;
    boolean running,manualOverride,waiting,releaseBrakes;long nextPlan,nextVisual;boolean visualDirty;int entry,status,stableTicks;UUID station;
    AutomaticTrains.Plan plan;Set<UUID> members;DriverConsoleEntity manualSource;
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
            if(entry.instruction.getClass()!=DestinationInstruction.class)return entry.instruction.getId().toString();
            for(var branch:entry.conditions)for(var c:branch)
                if(c.getClass()!=ScheduledDelay.class&&c.getClass()!=TimeOfDayCondition.class)return c.getId().toString();
        }
        return null;
    }
    boolean install(Player player,ItemStack held){
        if(!allowed(player)||Math.abs(owner.actual)>.15){hint(player,"stop_first");return false;}
        Schedule parsed;
        try{parsed=ScheduleItem.getSchedule(owner.getLevel().registryAccess(),held);}
        catch(RuntimeException error){hint(player,"invalid");return false;}
        String unsupported=unsupported(parsed);
        if(unsupported!=null){player.displayClientMessage(Component.translatable("gui.guidedrailways.auto.unsupported_item",unsupported),true);return false;}
        if(present())give(player);
        item=held.copyWithCount(1);if(!player.getAbilities().instabuild)held.shrink(1);program=parsed;
        entry=Math.clamp(parsed.savedProgress,0,parsed.entries.size()-1);waiting=false;station=null;resetConditions();members=null;manualOverride=false;manualSource=null;
        running=false;plan=null;state(PAUSED);owner.setChanged();hint(player,"installed");return true;
    }
    private boolean allowed(Player player){
        return owner.getLevel() instanceof ServerLevel level&&player.mayBuild()&&level.mayInteract(player,owner.getBlockPos())
            &&DriverControls.accessible(owner,player)&&(!owner.active||player.getUUID().equals(owner.operator));
    }
    boolean action(Player player,int id){
        if(!allowed(player))return false;
        if(id==31){pause(PAUSED);owner.park();hint(player,"paused");return true;}
        if(id==32){if(Math.abs(owner.actual)>.15){hint(player,"stop_first");return false;}pause(EMPTY);give(player);program=null;entry=0;waiting=false;station=null;resetConditions();owner.setChanged();return true;}
        if(id!=30||!present())return false;
        var p=program();String error=unsupported(p);if(error!=null){state(UNSUPPORTED);hint(player,"invalid");return false;}
        if(owner.emergency){hint(player,"emergency");return false;}
        if(DriverControls.body(owner)==null){hint(player,"install_body");return false;}
        if(entry>=p.entries.size()){entry=0;waiting=false;station=null;resetConditions();}
        owner.leave();owner.enabled=false;running=true;manualOverride=false;manualSource=null;plan=null;members=null;stableTicks=0;nextPlan=0;releaseBrakes=true;state(PLANNING);return true;
    }
    void manual(DriverConsoleEntity source){
        running=false;manualOverride=true;manualSource=source;plan=null;stableTicks=0;state(PAUSED);
    }
    void pause(int reason){running=false;manualOverride=false;manualSource=null;plan=null;stableTicks=0;state(reason);}
    void visual(int notch,int direction){
        if(owner.notch!=notch||owner.direction!=direction){owner.notch=notch;owner.direction=direction;visualDirty=true;owner.setChanged();}
        if(visualDirty&&owner.getLevel()!=null&&owner.getLevel().getGameTime()>=nextVisual){visualDirty=false;nextVisual=owner.getLevel().getGameTime()+5;owner.updateVisual();}
    }
    void state(int next){
        if(status==next)return;status=next;owner.setChanged();
        if(owner.getLevel() instanceof ServerLevel level)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[AutomaticTrain] console={} state={} entry={} target={}",owner.getBlockPos(),next,entry+1,station);
    }
    void give(Player player){if(!item.isEmpty()){var stack=item;item=ItemStack.EMPTY;player.getInventory().placeItemBackInInventory(stack);owner.setChanged();}}
    ItemStack removeItem(){var stack=item;item=ItemStack.EMPTY;program=null;pause(EMPTY);owner.setChanged();return stack;}
    DestinationInstruction instruction(){var p=program();return p!=null&&entry>=0&&entry<p.entries.size()&&p.entries.get(entry).instruction instanceof DestinationInstruction d?d:null;}
    void resetConditions(){progress.clear();contexts.clear();}
    boolean tickConditions(ServerLevel level){
        var branches=program().entries.get(entry).conditions;if(branches.isEmpty())return true;
        if(progress.size()!=branches.size()){resetConditions();for(var b:branches){progress.add(0);contexts.add(new CompoundTag());}}
        for(int i=0;i<branches.size();i++){
            var branch=branches.get(i);int index=progress.get(i);if(index>=branch.size())return true;
            var c=branch.get(index);
            // Verified for pinned Create 6.0.10: these EXACT classes do not dereference Train.
            // Never pass null to arbitrary third-party conditions.
            if(c.getClass()!=ScheduledDelay.class&&c.getClass()!=TimeOfDayCondition.class){pause(UNSUPPORTED);return false;}
            if(c.tickCompletion(level,null,contexts.get(i))){progress.set(i,index+1);contexts.set(i,new CompoundTag());if(index+1>=branch.size())return true;}
        }
        if(level.getGameTime()%20==0)owner.setChanged();return false;
    }
    void advance(){
        nextPlan=0;entry++;waiting=false;station=null;plan=null;stableTicks=0;resetConditions();
        if(entry>=program().entries.size()){
            if(program().cyclic)entry=0;else{running=false;state(FINISHED);return;}
        }
        state(PLANNING);owner.setChanged();
    }
    private static void hint(Player player,String key){player.displayClientMessage(Component.translatable("gui.guidedrailways.auto."+key),true);}
    void save(CompoundTag tag,HolderLookup.Provider registries){
        if(!present())return;
        var data=new CompoundTag();data.put("Item",item.save(registries));data.putInt("Entry",entry);data.putBoolean("Waiting",waiting);
        if(station!=null)data.putUUID("Station",station);
        var list=new ListTag();for(int i=0;i<progress.size();i++){var branch=new CompoundTag();branch.putInt("Progress",progress.get(i));branch.put("Context",contexts.get(i).copy());list.add(branch);}data.put("Conditions",list);
        tag.put("PhysicalSchedule",data);
    }
    void load(CompoundTag tag,HolderLookup.Provider registries){
        var data=tag.getCompound("PhysicalSchedule");item=ItemStack.parseOptional(registries,data.getCompound("Item"));program=null;
        entry=Math.max(0,data.getInt("Entry"));waiting=data.getBoolean("Waiting");station=data.hasUUID("Station")?data.getUUID("Station"):null;
        resetConditions();var list=data.getList("Conditions",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var b=list.getCompound(i);progress.add(Math.max(0,b.getInt("Progress")));contexts.add(b.getCompound("Context").copy());}
        running=false;manualOverride=false;manualSource=null;plan=null;members=null;stableTicks=0;status=present()?PAUSED:EMPTY;
    }
}
