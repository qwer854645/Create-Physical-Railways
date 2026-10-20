package com.guidedrailways;

import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * Optional hardware geometry, without hard references to the optional mod in shared endpoint code.
 */
public final class RailwayHardware {
  interface Bridge {
    CouplerKind kind(BlockState state);

    Vector3d pivot(CouplerEndpoint endpoint);

    Vector3d tip(CouplerEndpoint endpoint);
  }

  static Bridge bridge;

  public static boolean coupler(BlockState state) {
    return bridge != null && bridge.kind(state) != null;
  }

  static CouplerKind kind(CouplerEndpoint endpoint) {
    return endpoint.owner instanceof CouplerEntity
        ? CouplerKind.STANDARD
        : bridge == null ? null : bridge.kind(endpoint.getBlockState());
  }

  static Vector3d pivot(CouplerEndpoint endpoint) {
    var facing = endpoint.getBlockState().getValue(CouplerBlock.FACING);
    return endpoint.owner instanceof CouplerEntity
        ? CouplerVisual.pivot(facing.getStepX(), facing.getStepZ())
        : bridge.pivot(endpoint);
  }

  static Vector3d tip(CouplerEndpoint endpoint) {
    var facing = endpoint.getBlockState().getValue(CouplerBlock.FACING);
    return endpoint.owner instanceof CouplerEntity
        ? CouplerVisual.restTip(facing.getStepX(), facing.getStepZ())
        : bridge.tip(endpoint);
  }
}
