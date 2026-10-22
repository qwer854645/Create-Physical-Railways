package com.guidedrailways.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to packed positions; vanilla's union getter copies the entire chunk. */
@Mixin(ChunkAccess.class)
public interface ChunkPendingEntitiesAccessor {
  @Accessor("pendingBlockEntities")
  Map<BlockPos, CompoundTag> guidedrailways$pendingEntities();
}
