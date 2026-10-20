package com.guidedrailways.mixin;

import com.guidedrailways.ObserverActivation;
import com.simibubi.create.content.trains.observer.TrackObserver;
import org.spongepowered.asm.mixin.*;

@Mixin(value = TrackObserver.class, remap = false)
public abstract class TrackObserverMixin implements ObserverActivation {
  @Shadow private int activated;

  @Override
  public void guidedrailways$keepAlive() {
    activated = Math.max(activated, 8);
  }
}
