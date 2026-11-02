package com.physicalrailways.mixin;

import com.physicalrailways.ObserverActivation;
import com.simibubi.create.content.trains.observer.TrackObserver;
import org.spongepowered.asm.mixin.*;

@Mixin(value = TrackObserver.class, remap = false)
public abstract class TrackObserverMixin implements ObserverActivation {
  @Shadow private int activated;

  @Override
  public void physicalrailways$keepAlive() {
    activated = Math.max(activated, 8);
  }
}
