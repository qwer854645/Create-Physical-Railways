package com.guidedrailways;
import com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerServer;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.server.level.ServerPlayer;

final class SteamRailwaysBogeys implements PhysicalBogeySelection.Bridge {
    @Override public PhysicalBogeySelection.Selection selected(ServerPlayer player,RailGauge gauge){
        var selection=BogeyMenuHandlerServer.getStyle(player.getUUID());var style=selection.getFirst();var size=selection.getSecond();
        return resolve(style,size,gauge);
    }
    private static PhysicalBogeySelection.Selection resolve(com.simibubi.create.content.trains.bogey.BogeyStyle style,BogeySizes.BogeySize size,RailGauge gauge){
        // Native packets explicitly allow an unspecified size. Resolve it from the style,
        // and compare registered size IDs rather than requiring the very same Java object.
        if(size==null&&style.validSizes().contains(BogeySizes.SMALL))size=BogeySizes.SMALL;
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] requested style={} size={} gauge={}",style.id,size==null?"unspecified":size.id(),gauge);
        boolean small=size!=null&&size.id().equals(BogeySizes.SMALL.id());
        if(style.id.equals(AllBogeyStyles.STANDARD.id)&&small)return new PhysicalBogeySelection.Selection(BogeyVariant.DEFAULT,style.defaultData.copy());
        if(style.id.equals(CRBogeyStyles.MONOBOGEY.id)&&small){
            if(gauge!=RailGauge.MONORAIL)throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
            return new PhysicalBogeySelection.Selection(BogeyVariant.DEFAULT,style.defaultData.copy());
        }
        var variant=BogeyVariant.find(style.id.toString());
        if(variant==null||variant==BogeyVariant.DEFAULT||!small)throw new IllegalArgumentException("message.guidedrailways.bogey.unsupported");
        if(gauge!=RailGauge.STANDARD)throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
        return new PhysicalBogeySelection.Selection(variant,style.defaultData.copy());
    }
    static void verifyRegisteredStyles(){
        for(var style:java.util.List.of(AllBogeyStyles.STANDARD,CRBogeyStyles.FREIGHT,CRBogeyStyles.PASSENGER,CRBogeyStyles.MODERN,CRBogeyStyles.BLOMBERG,CRBogeyStyles.Y25)){
            var explicit=resolve(style,BogeySizes.SMALL,RailGauge.STANDARD);var unspecified=resolve(style,null,RailGauge.STANDARD);
            if(explicit.variant()!=unspecified.variant())throw new IllegalStateException("Native bogey size resolution differs for "+style.id);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] registered style checks passed: default + five double-axle variants, explicit and unspecified size");
    }
    private SteamRailwaysBogeys(){}
    static void register(){PhysicalBogeySelection.bridge=new SteamRailwaysBogeys();}
}
