package com.guidedrailways;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Physical-item selection is independent of optional menus overwriting each other's native state.
 */
public final class BogeySelectionNetwork {
  public record Select(ResourceLocation style, ResourceLocation size)
      implements CustomPacketPayload {
    static final Type<Select> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "bogey_selection"));
    static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeResourceLocation(v.style);
              b.writeResourceLocation(v.size);
            },
            b -> new Select(b.readResourceLocation(), b.readResourceLocation()));

    @Override
    public Type<Select> type() {
      return TYPE;
    }
  }

  static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            Select.TYPE,
            Select.CODEC,
            (packet, context) ->
                context.enqueueWork(
                    () -> {
                      if (!(context.player() instanceof ServerPlayer player)
                          || !(player.getMainHandItem().getItem() instanceof BogieItem)) return;
                      var style = AllBogeyStyles.BOGEY_STYLES.get(packet.style);
                      var size = BogeySizes.all().get(packet.size);
                      if (style == null
                          || size == null
                          || !style.validSizes().contains(size)
                          || BogeyVariant.find(packet.style.toString(), packet.size.toString())
                              == null) return;
                      var selected = new net.minecraft.nbt.CompoundTag();
                      selected.putString("Style", packet.style.toString());
                      selected.putString("Size", packet.size.toString());
                      player.getPersistentData().put("PhysicalRailwayBogey", selected);
                    }));
  }

  private BogeySelectionNetwork() {}
}
