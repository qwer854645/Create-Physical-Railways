package com.guidedrailways;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class DriverConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock,IWrenchable {
    public static final MapCodec<DriverConsoleBlock> CODEC=simpleCodec(DriverConsoleBlock::new);
    DriverConsoleBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(1,0,1,15,14,15);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new DriverConsoleEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
        return type==Content.DRIVER_ENTITY.get()?(world,pos,state,be)->{if(world instanceof net.minecraft.server.level.ServerLevel server&&be instanceof DriverConsoleEntity console)DriverControls.register(server,console);}:null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&l.getBlockEntity(p) instanceof DriverConsoleEntity console){DriverControls.register(server.serverLevel(),console);server.openMenu(console);}
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context){
        if(context.getPlayer()==null||!context.getPlayer().mayBuild()||!context.getLevel().mayInteract(context.getPlayer(),context.getClickedPos()))return InteractionResult.FAIL;
        if(!context.getLevel().isClientSide&&context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DriverConsoleEntity e){e.active=false;e.brake=1;}
        return IWrenchable.super.onWrenched(state,context);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
        if(!s.is(next.getBlock())){if(l.getBlockEntity(p) instanceof DriverConsoleEntity console&&l instanceof net.minecraft.server.level.ServerLevel server)DriverControls.removed(server,console);super.onRemove(s,l,p,next,moving);}
    }
}
