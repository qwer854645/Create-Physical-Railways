package com.guidedrailways;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import com.simibubi.create.foundation.block.IBE;
public final class BogieBlock extends ComponentBlock implements IBE<BogieEntity>,dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape,dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty STYLE=net.minecraft.world.level.block.state.properties.IntegerProperty.create("style",0,BogeyVariant.values().length-1);
    public static BogeyVariant variant(BlockState state){return BogeyVariant.read(state.getValue(STYLE));}
    BogieBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(STYLE,0));}
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(STYLE);}
    public static RailGauge gauge(BlockState state){return variant(state).gauge;}
    @Override public VoxelShape getSubLevelCollisionShape(BlockGetter level,BlockState state){return BogieCollisionShape.centre(variant(state));}
    @Override public org.joml.Vector3dc getCenterOfMass(BlockGetter level,BlockState state){return new org.joml.Vector3d(.5,BogieCollision.massCenterY(),.5);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return BogieCollisionShape.centre(variant(s));}
    @Override public Class<BogieEntity> getBlockEntityClass(){return BogieEntity.class;}
    @Override public BlockEntityType<? extends BogieEntity> getBlockEntityType(){return Content.BOGIE_ENTITY.get();}
}
