package com.guidedrailways;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** The original block keeps its save format while sharing endpoint state with optional native hardware. */
public final class CouplerEntity extends BlockEntity {
    final CouplerEndpoint endpoint=new CouplerEndpoint(this);
    CouplerEntity(BlockPos pos,BlockState state){super(Content.COUPLER_ENTITY.get(),pos,state);}
    void clientTick(){endpoint.clientTick();}
    @Override public void onLoad(){super.onLoad();if(level instanceof net.minecraft.server.level.ServerLevel server)CouplerConnections.register(server,endpoint);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);endpoint.write(tag);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);endpoint.read(tag);}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=saveWithoutMetadata(registries);tag.merge(endpoint.save());return tag;}
    @Override public void setRemoved(){endpoint.unloaded();super.setRemoved();}
}
