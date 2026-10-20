package com.guidedrailways;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** No optional mod types in the core item/placement path. */
final class PhysicalBogeySelection {
  /** Null gauge is the standard style's automatic adaptation to the targeted rail. */
  record Selection(RailGauge gauge, BogeyVariant variant, CompoundTag data) {}

  interface Bridge {
    Selection selected(ServerPlayer player);
  }

  static Bridge bridge;
  static Runnable menu;
  static Component selectedName;
  static java.util.function.Consumer<List<Component>> tooltip;

  static Selection selected(ServerPlayer player) {
    var selected = player.getPersistentData().getCompound("PhysicalRailwayBogey");
    if (selected.contains("Style") && selected.contains("Size")) {
      var id = net.minecraft.resources.ResourceLocation.tryParse(selected.getString("Style"));
      var sizeId = net.minecraft.resources.ResourceLocation.tryParse(selected.getString("Size"));
      var variant = BogeyVariant.find(selected.getString("Style"), selected.getString("Size"));
      var style = id == null ? null : com.simibubi.create.AllBogeyStyles.BOGEY_STYLES.get(id);
      var size =
          sizeId == null
              ? null
              : com.simibubi.create.content.trains.bogey.BogeySizes.all().get(sizeId);
      if (variant != null && style != null && size != null && style.validSizes().contains(size)) {
        boolean automatic =
            id.equals(com.simibubi.create.AllBogeyStyles.STANDARD.id)
                && size == com.simibubi.create.content.trains.bogey.BogeySizes.SMALL;
        return new Selection(automatic ? null : variant.gauge, variant, style.defaultData.copy());
      }
    }
    return bridge == null
        ? new Selection(RailGauge.STANDARD, BogeyVariant.DEFAULT, new CompoundTag())
        : bridge.selected(player);
  }

  private PhysicalBogeySelection() {}
}
