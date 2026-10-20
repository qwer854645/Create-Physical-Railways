package com.guidedrailways.mixin;

import com.guidedrailways.MonorailContacts;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Use Sable's contact callback without replacing Railway's player collision or rail model. */
@Pseudo
@Mixin(
    targets = "com.railwayteam.railways.content.custom_tracks.monorail.MonorailTrackBlock",
    remap = false)
public abstract class MonorailTrackContactMixin implements BlockWithSubLevelCollisionCallback {
  @Override
  public BlockSubLevelCollisionCallback sable$getCallback() {
    return MonorailContacts.INSTANCE;
  }
}
