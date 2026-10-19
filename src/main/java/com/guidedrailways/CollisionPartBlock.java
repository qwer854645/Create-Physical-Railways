package com.guidedrailways;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;
/** Invisible collision cells in the existing bogie sublevel; no block entities and no extra bodies. */
public final class CollisionPartBlock extends ComponentBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape,dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,127);
    CollisionPartBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(PART,0).setValue(BogieBlock.GAUGE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(PART,BogieBlock.GAUGE);}
    @Override public VoxelShape getSubLevelCollisionShape(BlockGetter level,BlockState state){return BogieCollisionShape.get(state.getValue(PART),BogieBlock.gauge(state));}
    @Override public org.joml.Vector3dc getCenterOfMass(BlockGetter level,BlockState state){return new org.joml.Vector3d(.5,BogieCollision.massCenterY(),.5);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return BogieCollisionShape.get(state.getValue(PART),BogieBlock.gauge(state));}
}
