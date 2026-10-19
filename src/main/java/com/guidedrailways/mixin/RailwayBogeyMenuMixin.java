package com.guidedrailways.mixin;
import com.guidedrailways.SteamRailwaysClient;
import com.railwayteam.railways.api.bogeymenu.v0.entry.BogeyEntry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe confirmed native selection, leaving its packet and menu intact. Client only. */
@Pseudo
@Mixin(targets="com.railwayteam.railways.content.bogey_menu.BogeyMenuScreen",remap=false)
public abstract class RailwayBogeyMenuMixin {
    @Shadow BogeyEntry selectedBogey;
    @Inject(method="onMenuClose",at=@At("HEAD"))
    private void guidedrailways$selection(CallbackInfo ci){if(selectedBogey!=null)SteamRailwaysClient.selection(selectedBogey.bogeyStyle());}
}
