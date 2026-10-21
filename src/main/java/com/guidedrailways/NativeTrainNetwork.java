package com.guidedrailways;

import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Native AddTrainPacket decodes an ordinary Train; proxy snapshots preserve the query-only type.
 */
public final class NativeTrainNetwork {
  static Consumer<CompoundTag> client = tag -> {};

  public record Update(CompoundTag data) implements CustomPacketPayload {
    static final Type<Update> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "train_identity"));
    static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC =
        StreamCodec.of(
            (b, value) -> b.writeNbt(value.data),
            b -> new Update(java.util.Objects.requireNonNull(b.readNbt())));

    @Override
    public Type<Update> type() {
      return TYPE;
    }
  }

  static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            Update.TYPE,
            Update.CODEC,
            (packet, context) -> context.enqueueWork(() -> client.accept(packet.data)));
  }

  private NativeTrainNetwork() {}
}
