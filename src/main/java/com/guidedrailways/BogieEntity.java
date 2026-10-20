package com.guidedrailways;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public final class BogieEntity extends SmartBlockEntity {
  public double distance, speed;
  public CompoundTag bogeyData = new CompoundTag();

  public BogieEntity(BlockPos p, BlockState s) {
    super(Content.BOGIE_ENTITY.get(), p, s);
  }

  @Override
  public void addBehaviours(java.util.List<BlockEntityBehaviour> list) {}

  @Override
  protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
    var b = BogieRenderEnvelope.get(BogieBlock.variant(getBlockState()));
    return new net.minecraft.world.phys.AABB(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1())
        .move(worldPosition);
  }

  @Override
  protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
    super.write(tag, registries, client);
    tag.putDouble("WheelDistance", distance);
    tag.putDouble("LinearSpeed", speed);
    tag.putString("NativeBogeyStyle", BogieBlock.variant(getBlockState()).id);
    tag.putString("NativeBogeySize", BogieBlock.variant(getBlockState()).size);
    tag.put("BogeyData", bogeyData.copy());
  }

  @Override
  protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
    super.read(tag, registries, client);
    distance = tag.getDouble("WheelDistance");
    speed = tag.getDouble("LinearSpeed");
    bogeyData = tag.getCompound("BogeyData").copy();
  }
}
