package com.physicalrailways.mixin;

import com.physicalrailways.PhysicalTrainProxy;
import com.physicalrailways.TrainName;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.foundation.gui.widget.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Allow native identity editing while retaining physical assembly restrictions. */
@Mixin(value = StationScreen.class, remap = false)
public abstract class PhysicalStationScreenMixin extends AbstractStationScreen {
  @Shadow private EditBox trainNameBox;
  @Shadow private IconButton disassembleTrainButton;
  @Shadow private IconButton newTrainButton;
  @Shadow private ScrollInput colorTypeScroll;

  @Unique
  private final Component physicalrailways$assemblyHint =
      Component.translatable("gui.physicalrailways.station.no_assembly");

  protected PhysicalStationScreenMixin(StationBlockEntity block, GlobalStation station) {
    super(block, station);
  }

  @Inject(method = "tick", at = @At("TAIL"), require = 1)
  private void physicalrailways$physicalStationView(CallbackInfo ci) {
    var train = displayedTrain.get();
    boolean physical = train instanceof PhysicalTrainProxy;
    trainNameBox.setEditable(true);
    trainNameBox.setMaxLength(physical ? TrainName.MAX_LENGTH : 35);
    trainNameBox.setTooltip(null);
    if (!physical) return;
    colorTypeScroll.active = true;
    colorTypeScroll.visible = true;
    newTrainButton.active = false;
    disassembleTrainButton.active = false;
    disassembleTrainButton.setToolTip(physicalrailways$assemblyHint);
  }
}
