package com.guidedrailways.mixin;

import com.guidedrailways.CouplerEndpoint;
import com.guidedrailways.NativeHardwareShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.*;

/** Native selectable geometry becomes actual collision geometry; no substitute block or model. */
@Pseudo
@Mixin(
    targets = {
      "com.railwayteam.railways.content.buffer.single_deco.AbstractDyeableSingleBufferBlock",
      "com.railwayteam.railways.content.buffer.headstock.HeadstockBlock",
      "com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlock"
    },
    remap = false)
public abstract class RailwayHardwareBlockMixin extends Block
    implements BlockSubLevelCollisionShape, BlockSubLevelAssemblyListener {
  protected RailwayHardwareBlockMixin(Properties properties) {
    super(properties);
  }

  @Override
  public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
    return NativeHardwareShape.get(
        state, true, state.getShape(level, BlockPos.ZERO, CollisionContext.empty()));
  }

  @Override
  protected VoxelShape getCollisionShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return NativeHardwareShape.get(state, false, state.getShape(level, pos, context));
  }

  @Override
  public void beforeMove(
      ServerLevel from, ServerLevel to, BlockState state, BlockPos oldPos, BlockPos newPos) {
    CouplerEndpoint.beforeMove(from, to, oldPos, newPos);
  }

  @Override
  public void afterMove(
      ServerLevel from, ServerLevel to, BlockState state, BlockPos oldPos, BlockPos newPos) {
    CouplerEndpoint.afterMove(to, newPos);
  }
}
