package com.guidedrailways;
import com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerServer;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.server.level.ServerPlayer;

final class SteamRailwaysBogeys implements PhysicalBogeySelection.Bridge {
    @Override public PhysicalBogeySelection.Selection selected(ServerPlayer player){
        var selection=BogeyMenuHandlerServer.getStyle(player.getUUID());var style=selection.getFirst();var size=selection.getSecond();
        return resolve(style,size);
    }
    private static PhysicalBogeySelection.Selection resolve(com.simibubi.create.content.trains.bogey.BogeyStyle style,BogeySizes.BogeySize size){
        // Native packets explicitly allow an unspecified size. Resolve it from the style,
        // and compare registered size IDs rather than requiring the very same Java object.
        if(size==null&&style.validSizes().contains(BogeySizes.SMALL))size=BogeySizes.SMALL;
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] requested style={} size={}",style.id,size==null?"unspecified":size.id());
        boolean small=size!=null&&size.id().equals(BogeySizes.SMALL.id());
        if(style.id.equals(AllBogeyStyles.STANDARD.id)&&small)return new PhysicalBogeySelection.Selection(null,BogeyVariant.DEFAULT,style.defaultData.copy());
        if(style.id.equals(CRBogeyStyles.MONOBOGEY.id)&&small){
            return new PhysicalBogeySelection.Selection(RailGauge.MONORAIL,BogeyVariant.DEFAULT,style.defaultData.copy());
        }
        if(style.id.equals(CRBogeyStyles.WIDE_DEFAULT.id)&&small)return new PhysicalBogeySelection.Selection(RailGauge.WIDE,BogeyVariant.DEFAULT,style.defaultData.copy());
        if(style.id.equals(CRBogeyStyles.NARROW_DEFAULT.id)&&small)return new PhysicalBogeySelection.Selection(RailGauge.NARROW,BogeyVariant.DEFAULT,style.defaultData.copy());
        var variant=BogeyVariant.find(style.id.toString());
        if(variant==null||variant==BogeyVariant.DEFAULT||!small)throw new IllegalArgumentException("message.guidedrailways.bogey.unsupported");
        return new PhysicalBogeySelection.Selection(RailGauge.STANDARD,variant,style.defaultData.copy());
    }
    static void verifyRegisteredStyles(){
        for(var style:java.util.List.of(AllBogeyStyles.STANDARD,CRBogeyStyles.FREIGHT,CRBogeyStyles.PASSENGER,CRBogeyStyles.MODERN,CRBogeyStyles.BLOMBERG,CRBogeyStyles.Y25,CRBogeyStyles.WIDE_DEFAULT,CRBogeyStyles.NARROW_DEFAULT,CRBogeyStyles.MONOBOGEY)){
            var explicit=resolve(style,BogeySizes.SMALL);var unspecified=resolve(style,null);
            if(explicit.variant()!=unspecified.variant()||explicit.gauge()!=unspecified.gauge())throw new IllegalStateException("Native bogey size resolution differs for "+style.id);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] registered style checks passed: automatic default + five double-axle variants + wide/narrow/mono defaults");
    }
    private SteamRailwaysBogeys(){}
    static void register(){PhysicalBogeySelection.bridge=new SteamRailwaysBogeys();}
}
