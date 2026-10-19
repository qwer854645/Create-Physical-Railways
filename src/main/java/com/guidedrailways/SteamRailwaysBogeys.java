package com.guidedrailways;
import com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerServer;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.server.level.ServerPlayer;

final class SteamRailwaysBogeys implements PhysicalBogeySelection.Bridge {
    @Override public PhysicalBogeySelection.Selection selected(ServerPlayer player,RailGauge gauge){
        var selection=BogeyMenuHandlerServer.getStyle(player.getUUID());var style=selection.getFirst();var size=selection.getSecond();
        if(style==AllBogeyStyles.STANDARD&&size==BogeySizes.SMALL)return new PhysicalBogeySelection.Selection(BogeyVariant.DEFAULT,style.defaultData.copy());
        if(style==CRBogeyStyles.MONOBOGEY&&size==BogeySizes.SMALL){
            if(gauge!=RailGauge.MONORAIL)throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
            return new PhysicalBogeySelection.Selection(BogeyVariant.DEFAULT,style.defaultData.copy());
        }
        var variant=BogeyVariant.find(style.id.toString());
        if(variant==null||variant==BogeyVariant.DEFAULT||size!=BogeySizes.SMALL)throw new IllegalArgumentException("message.guidedrailways.bogey.unsupported");
        if(gauge!=RailGauge.STANDARD)throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
        return new PhysicalBogeySelection.Selection(variant,style.defaultData.copy());
    }
    private SteamRailwaysBogeys(){}
    static void register(){PhysicalBogeySelection.bridge=new SteamRailwaysBogeys();}
}
