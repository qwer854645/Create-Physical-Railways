package com.guidedrailways;
import dev.simulated_team.simulated.content.blocks.docking_connector.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.item.ItemStack;
import java.util.List;

final class RailDockBlock extends DockingConnectorBlock {
    RailDockBlock(Properties properties){super(properties);}
    @Override public BlockEntityType<? extends DockingConnectorBlockEntity> getBlockEntityType(){return RailDockContent.ENTITY.get();}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(new ItemStack(this));}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&level instanceof ServerLevel server)RailDockIndex.get(server).bind(pos,null);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void afterMove(ServerLevel from,ServerLevel to,BlockState state,BlockPos a,BlockPos b){
        super.afterMove(from,to,state,a,b);RailDockIndex.get(from).bind(a,null);
        if(to.getBlockEntity(b) instanceof RailDockEntity dock){
            if(from!=to){dock.stationId=null;dock.stationPos=null;dock.setChanged();}
            dock.refreshBinding();
        }
    }
}
