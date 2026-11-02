package com.guidedrailways.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access only the vanilla entity persistence barrier; no tick or teleport behavior is injected. */
@Mixin(ServerLevel.class)
public interface ServerEntityStorageAccessor {
  @Accessor("entityManager") PersistentEntitySectionManager<Entity> guidedrailways$entityStorage();
}
