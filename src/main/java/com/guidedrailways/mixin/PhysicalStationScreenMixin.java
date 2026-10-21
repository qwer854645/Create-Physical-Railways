package com.guidedrailways.mixin;

import com.guidedrailways.PhysicalTrainProxy;
import com.guidedrailways.TrainName;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.foundation.gui.widget.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the native station view, with onboard controls as the physical identity editor. */
@Mixin(value = StationScreen.class, remap = false)
public abstract class PhysicalStationScreenMixin extends AbstractStationScreen {
  @Shadow private EditBox trainNameBox;
  @Shadow private IconButton disassembleTrainButton;
  @Shadow private IconButton newTrainButton;
  @Shadow private ScrollInput colorTypeScroll;

  @Unique
  private final Tooltip guidedrailways$nameHint =
      Tooltip.create(Component.translatable("gui.guidedrailways.station.controls_name"));

  @Unique
  private final Component guidedrailways$assemblyHint =
      Component.translatable("gui.guidedrailways.station.no_assembly");

  protected PhysicalStationScreenMixin(StationBlockEntity block, GlobalStation station) {
    super(block, station);
  }

  @Inject(method = "tick", at = @At("TAIL"), require = 1)
  private void guidedrailways$physicalStationView(CallbackInfo ci) {
    var train = displayedTrain.get();
    boolean physical = train instanceof PhysicalTrainProxy;
    trainNameBox.setEditable(!physical);
    trainNameBox.setMaxLength(physical ? TrainName.MAX_LENGTH : 35);
    trainNameBox.setTooltip(physical ? guidedrailways$nameHint : null);
    if (!physical) return;
    trainNameBox.setFocused(false);
    if (!trainNameBox.getValue().equals(train.name.getString()))
      trainNameBox.setValue(train.name.getString());
    colorTypeScroll.active = false;
    colorTypeScroll.visible = false;
    newTrainButton.active = false;
    disassembleTrainButton.active = false;
    disassembleTrainButton.setToolTip(guidedrailways$assemblyHint);
  }

  @Inject(method = "syncTrainNameAndColor", at = @At("HEAD"), cancellable = true, require = 1)
  private void guidedrailways$readOnlyIdentity(CallbackInfo ci) {
    if (displayedTrain.get() instanceof PhysicalTrainProxy) ci.cancel();
  }
}
