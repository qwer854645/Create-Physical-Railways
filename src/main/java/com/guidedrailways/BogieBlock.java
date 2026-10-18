package com.guidedrailways;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import com.simibubi.create.foundation.block.IBE;
public final class BogieBlock extends ComponentBlock implements IBE<BogieEntity>,dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape,dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass {
    BogieBlock(Properties p){super(p);}
    @Override public VoxelShape getSubLevelCollisionShape(BlockGetter level,BlockState state){return BogieCollisionShape.get(BogieCollision.CENTRE_PART);}
    @Override public org.joml.Vector3dc getCenterOfMass(BlockGetter level,BlockState state){return new org.joml.Vector3d(.5,BogieCollision.massCenterY(),.5);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return BogieCollisionShape.get(BogieCollision.CENTRE_PART);}
    @Override public Class<BogieEntity> getBlockEntityClass(){return BogieEntity.class;}
    @Override public BlockEntityType<? extends BogieEntity> getBlockEntityType(){return Content.BOGIE_ENTITY.get();}
}
