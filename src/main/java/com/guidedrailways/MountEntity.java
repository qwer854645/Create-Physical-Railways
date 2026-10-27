package com.guidedrailways;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public final class MountEntity extends KineticBlockEntity {
  UUID bogieId;
  MountHandoff handoff;
  boolean braking;
  double load;
  RailGauge gauge = RailGauge.STANDARD;
  BogeyVariant variant = BogeyVariant.DEFAULT;

  double pivotHeight() {
    return getBlockState().getValue(MountBlock.COMPACT)
        ? variant.compactPivotHeight()
        : variant.pivotHeight();
  }

  org.joml.Quaterniond pivotReference = new org.joml.Quaterniond();
  boolean pivotReferenceKnown;

  public MountEntity(BlockPos p, BlockState s) {
    super(Content.MOUNT_ENTITY.get(), p, s);
  }

  @Override
  public void tick() {
    super.tick();
    MountBlock.restore(this);
    VehiclePhysics.register(this);
  }

  void setLoad(double mass) {
    if (Math.abs(mass - load) < .01) return;
    load = mass;
    setChanged();
    if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
  }

  @Override
  public float calculateStressApplied() {
    lastStressApplied =
        bogieId == null ? 0 : (float) Math.max(.1, load / TrainConfig.STRESS_MASS.get());
    return lastStressApplied;
  }

  @Override
  protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
    super.write(tag, registries, client);
    if (bogieId != null) tag.putUUID("BogieBody", bogieId);
    if (handoff != null) tag.put("MountHandoff", handoff.save());
    else tag.remove("MountHandoff");
    tag.putBoolean("ServiceBrake", braking);
    tag.putDouble("SupportedMass", load);
    tag.putString("RailGauge", gauge.name());
    tag.putInt("BogeyVariant", variant.ordinal());
    if (pivotReferenceKnown) {
      var ref = new CompoundTag();
      ref.putDouble("X", pivotReference.x);
      ref.putDouble("Y", pivotReference.y);
      ref.putDouble("Z", pivotReference.z);
      ref.putDouble("W", pivotReference.w);
      tag.put("PivotReference", ref);
    }
  }

  @Override
  protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {

      if (client) SyncWatch.note(MountEntity.class);
    super.read(tag, registries, client);
    bogieId = tag.hasUUID("BogieBody") ? tag.getUUID("BogieBody") : null;
    handoff = MountHandoff.load(tag.getCompound("MountHandoff"));
    braking = tag.getBoolean("ServiceBrake");
    load = tag.getDouble("SupportedMass");
    gauge = RailGauge.read(tag.getString("RailGauge"));
    variant = BogeyVariant.read(tag.getInt("BogeyVariant"));
    var ref = tag.getCompound("PivotReference");
    pivotReference.set(
        ref.getDouble("X"), ref.getDouble("Y"), ref.getDouble("Z"), ref.getDouble("W"));
    pivotReferenceKnown =
        tag.contains("PivotReference")
            && pivotReference.isFinite()
            && pivotReference.lengthSquared() > .5;
    if (pivotReferenceKnown) pivotReference.normalize();
    else pivotReference.identity();
  }
}
