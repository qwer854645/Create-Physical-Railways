package com.guidedrailways;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** No optional mod types in the core item/placement path. */
final class PhysicalBogeySelection {
    record Selection(BogeyVariant variant,CompoundTag data){}
    interface Bridge {Selection selected(ServerPlayer player,RailGauge gauge);}
    static Bridge bridge;
    static java.util.function.Consumer<List<Component>> tooltip;
    static Selection selected(ServerPlayer player,RailGauge gauge){return bridge==null?new Selection(BogeyVariant.DEFAULT,new CompoundTag()):bridge.selected(player,gauge);}
    private PhysicalBogeySelection(){}
}
