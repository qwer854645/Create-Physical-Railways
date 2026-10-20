package com.guidedrailways;
import com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerServer;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.*;
import net.minecraft.server.level.ServerPlayer;
final class SteamRailwaysBogeys implements PhysicalBogeySelection.Bridge {
    @Override public PhysicalBogeySelection.Selection selected(ServerPlayer player){var s=BogeyMenuHandlerServer.getStyle(player.getUUID());return resolve(s.getFirst(),s.getSecond());}
    private static PhysicalBogeySelection.Selection resolve(BogeyStyle style,BogeySizes.BogeySize size){
        if(size==null)size=style.validSizes().stream().sorted(java.util.Comparator.comparing(s->s.id().toString())).findFirst().orElse(null);
        var variant=size==null?null:BogeyVariant.find(style.id.toString(),size.id().toString());
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] requested style={} size={} variant={}",style.id,size==null?"unspecified":size.id(),variant);
        if(variant==null)throw new IllegalArgumentException("message.guidedrailways.bogey.unsupported");
        boolean automatic=style.id.equals(AllBogeyStyles.STANDARD.id)&&size.id().equals(BogeySizes.SMALL.id());
        return new PhysicalBogeySelection.Selection(automatic?null:variant.gauge,variant,style.defaultData.copy());
    }
    static void verifyRegisteredStyles(){
        int count=0;for(var style:AllBogeyStyles.BOGEY_STYLES.values())if(style.id.getNamespace().equals("railways")||style==AllBogeyStyles.STANDARD)for(var size:style.validSizes()){resolve(style,size);count++;}
        if(count!=BogeyVariant.values().length-1)throw new IllegalStateException("Native bogey catalog mismatch: "+count);
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogeySelection] all {} installed native style/size profiles resolved",count);
    }
    private SteamRailwaysBogeys(){}
    static void register(){PhysicalBogeySelection.bridge=new SteamRailwaysBogeys();}
}
