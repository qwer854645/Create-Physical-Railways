package com.guidedrailways;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
public final class CouplerBlock extends HorizontalDirectionalBlock implements IWrenchable,IBE<CouplerEntity>,BlockSubLevelAssemblyListener,dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape {
    public static final MapCodec<CouplerBlock> CODEC=simpleCodec(CouplerBlock::new);
    private static final Map<Direction,VoxelShape> SHAPES=shapes();
    CouplerBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var face=c.getClickedFace();return defaultBlockState().setValue(FACING,face.getAxis().isHorizontal()?face:c.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES.get(s.getValue(FACING));}
    @Override public VoxelShape getSubLevelCollisionShape(BlockGetter level,BlockState state){return SHAPES.get(state.getValue(FACING));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        var fixed=SHAPES.get(state.getValue(FACING));
        // Sable 2.0.3 queries ordinary entity movement with the no-context overload.
        // Native rigid bodies use getSubLevelCollisionShape above and keep the fixed buffer.
        if((!(context instanceof EntityCollisionContext entity)||entity.getEntity() instanceof net.minecraft.world.entity.LivingEntity)
                &&level.getBlockEntity(pos) instanceof CouplerEntity coupler)
            return Shapes.or(fixed,CouplerLivingShape.get(coupler));
        return fixed;
    }
    private static Map<Direction,VoxelShape> shapes(){
        var base=Shapes.or(box(3,4,12,13,12,16),box(4,5,8,12,11,14));
        var map=new EnumMap<Direction,VoxelShape>(Direction.class);map.put(Direction.NORTH,base);
        for(var direction:List.of(Direction.EAST,Direction.SOUTH,Direction.WEST)){
            var next=Shapes.empty();for(var a:base.toAabbs())next=Shapes.or(next,Shapes.box(1-a.maxZ,a.minY,a.minX,1-a.minZ,a.maxY,a.maxX));
            map.put(direction,next);base=next;
        }return Map.copyOf(map);
    }
    @Override public Class<CouplerEntity> getBlockEntityClass(){return CouplerEntity.class;}
    @Override public BlockEntityType<? extends CouplerEntity> getBlockEntityType(){return Content.COUPLER_ENTITY.get();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CouplerEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        if(type!=Content.COUPLER_ENTITY.get())return null;
        return (l,p,s,be)->{if(be instanceof CouplerEntity coupler){if(l instanceof ServerLevel server)CouplerConnections.register(server,coupler);else coupler.clientTick();}};
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext c){
        if(c.getPlayer()==null||!c.getPlayer().mayBuild()||!c.getLevel().mayInteract(c.getPlayer(),c.getClickedPos()))return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)||!(c.getLevel() instanceof ServerLevel level)||!(level.getBlockEntity(c.getClickedPos()) instanceof CouplerEntity e))return InteractionResult.FAIL;
        try{CouplerConnections.wrench(level,e,player);player.swing(c.getHand(),true);return InteractionResult.CONSUME;}
        catch(IllegalArgumentException|IllegalStateException error){player.displayClientMessage(UserText.error(error),true);return InteractionResult.FAIL;}
    }
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext c){
        if(c.getPlayer()==null||!c.getPlayer().mayBuild()||!c.getLevel().mayInteract(c.getPlayer(),c.getClickedPos()))return InteractionResult.FAIL;
        return IWrenchable.super.onSneakWrenched(state,c);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof CouplerEntity e)CouplerConnections.destroyed(server,e);
        IBE.onRemove(state,level,pos,next);super.onRemove(state,level,pos,next,moving);
    }
    @Override public void beforeMove(ServerLevel from,ServerLevel to,BlockState state,BlockPos oldPos,BlockPos newPos){CouplerConnections.beforeMove(from,to,oldPos,newPos);}
    @Override public void afterMove(ServerLevel from,ServerLevel to,BlockState state,BlockPos oldPos,BlockPos newPos){CouplerConnections.afterMove(to,newPos);}
}
