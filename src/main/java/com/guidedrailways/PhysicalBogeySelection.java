package com.guidedrailways;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** No optional mod types in the core item/placement path. */
final class PhysicalBogeySelection {
    /** Null gauge is the standard style's automatic adaptation to the targeted rail. */
    record Selection(RailGauge gauge,BogeyVariant variant,CompoundTag data){}
    interface Bridge {Selection selected(ServerPlayer player);}
    static Bridge bridge;
    static java.util.function.Consumer<List<Component>> tooltip;
    static Selection selected(ServerPlayer player){return bridge==null?new Selection(RailGauge.STANDARD,BogeyVariant.DEFAULT,new CompoundTag()):bridge.selected(player);}
    private PhysicalBogeySelection(){}
}
