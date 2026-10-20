package com.guidedrailways;

import com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlock;
import com.railwayteam.railways.content.buffer.headstock.HeadstockBlock;
import com.railwayteam.railways.content.buffer.single_deco.LinkPinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

final class SteamRailwaysHardware implements RailwayHardware.Bridge {
  @Override
  public CouplerKind kind(BlockState state) {
    if (state.getBlock() instanceof LinkPinBlock)
      return CouplerKind.railway(state.getValue(LinkPinBlock.STYLE).getSerializedName());
    if (!(state.getBlock() instanceof HeadstockBlock
        || state.getBlock() instanceof CopycatHeadstockBlock)) return null;
    String style = state.getValue(HeadstockBlock.STYLE).getSerializedName();
    return CouplerKind.railway(style);
  }

  private Vector3d point(CouplerEndpoint endpoint, boolean front) {
    var state = endpoint.getBlockState();
    var facing = state.getValue(CouplerBlock.FACING);
    var kind = kind(state);
    double shift = state.getBlock() instanceof LinkPinBlock ? 0 : -3 / 16.0;
    double z =
        front
            ? (kind == CouplerKind.SCREWLINK
                ? .5625
                : kind == CouplerKind.LINK || kind == CouplerKind.LINKLESS ? .375 : .25)
            : (kind == CouplerKind.SCREWLINK
                ? .75
                : kind == CouplerKind.LINK || kind == CouplerKind.LINKLESS ? .5 : .875);
    return new Vector3d(0, 0, z + shift - .5)
        .rotate(
            new org.joml.Quaterniond()
                .rotationTo(
                    new Vector3d(0, 0, -1), new Vector3d(facing.getStepX(), 0, facing.getStepZ())))
        .add(.5, .5, .5);
  }

  @Override
  public Vector3d pivot(CouplerEndpoint endpoint) {
    return point(endpoint, false);
  }

  @Override
  public Vector3d tip(CouplerEndpoint endpoint) {
    return point(endpoint, true);
  }
}
