package com.physicalrailways.mixin;

import com.physicalrailways.CouplerEndpoint;
import com.physicalrailways.RailwayHardware;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
      "com.railwayteam.railways.content.buffer.DyeableBlockEntity",
      "com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlockEntity"
    },
    remap = false)
public abstract class RailwayHardwareEntityMixin extends SmartBlockEntity
    implements CouplerEndpoint.Provider {
  @Unique private CouplerEndpoint physicalrailways$coupler;

  protected RailwayHardwareEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  @Override
  public CouplerEndpoint physicalrailways$endpoint() {
    if (!RailwayHardware.coupler(getBlockState())) return null;
    if (physicalrailways$coupler == null) physicalrailways$coupler = new CouplerEndpoint(this);
    return physicalrailways$coupler;
  }

  @Override
  public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
    boolean dynamic =
        com.physicalrailways.HardwareRenderData.ready
            && RailwayHardware.coupler(getBlockState())
            && getLevel() != null
            && dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(
                    getLevel(), getBlockPos())
                != null;
    return super.getModelData()
        .derive()
        .with(com.physicalrailways.HardwareRenderData.DYNAMIC, dynamic)
        .build();
  }

  @Override
  public void tick() {
    super.tick();
    var endpoint = physicalrailways$endpoint();
    if (endpoint != null) endpoint.tick();
    else if (physicalrailways$coupler != null) {
      physicalrailways$coupler.destroyed();
      physicalrailways$coupler = null;
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    var endpoint = physicalrailways$endpoint();
    if (endpoint != null && getLevel() instanceof net.minecraft.server.level.ServerLevel)
      endpoint.tick();
  }

  @Override
  public void invalidate() {
    if (physicalrailways$coupler != null) physicalrailways$coupler.unloaded();
    super.invalidate();
  }

  @Override
  public void destroy() {
    if (physicalrailways$coupler != null) physicalrailways$coupler.destroyed();
    super.destroy();
  }

  @Inject(method = "write", at = @At("TAIL"))
  private void physicalrailways$write(
      CompoundTag tag, HolderLookup.Provider registries, boolean client, CallbackInfo ci) {
    if (physicalrailways$coupler != null) tag.put("GuidedCoupler", physicalrailways$coupler.save());
  }

  @Inject(method = "read", at = @At("TAIL"))
  private void physicalrailways$read(
      CompoundTag tag, HolderLookup.Provider registries, boolean client, CallbackInfo ci) {
    var endpoint = physicalrailways$endpoint();
    if (endpoint != null && tag.contains("GuidedCoupler", 10))
      endpoint.read(tag.getCompound("GuidedCoupler"));
  }
}
