package com.guidedrailways;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import com.simibubi.create.foundation.block.IBE;
public final class BogieBlock extends ComponentBlock implements IBE<BogieEntity> {
    BogieBlock(Properties p){super(p);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return BogieCollisionShape.SHAPE;}
    @Override public Class<BogieEntity> getBlockEntityClass(){return BogieEntity.class;}
    @Override public BlockEntityType<? extends BogieEntity> getBlockEntityType(){return Content.BOGIE_ENTITY.get();}
}
