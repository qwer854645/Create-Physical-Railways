package com.guidedrailways;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Driving has no container/screen; commands bind to the server's actual operator and endpoint. */
public final class DriverNetwork {
  static java.util.function.Consumer<State> clientHandler = state -> {};

  public record Command(BlockPos pos, int command) implements CustomPacketPayload {
    static final Type<Command> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "driver_command"));
    static final StreamCodec<RegistryFriendlyByteBuf, Command> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeBlockPos(v.pos);
              b.writeVarInt(v.command);
            },
            b -> new Command(b.readBlockPos(), b.readVarInt()));

    @Override
    public Type<Command> type() {
      return TYPE;
    }
  }

  public record State(
      boolean active,
      BlockPos pos,
      int notch,
      int direction,
      boolean emergency,
      double speed,
      double available,
      int powered,
      int supports,
      boolean observing,
      double gearTarget)
      implements CustomPacketPayload {
    static final Type<State> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "driver_state"));
    static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeBoolean(v.active);
              b.writeBlockPos(v.pos);
              b.writeVarInt(v.notch);
              b.writeVarInt(v.direction);
              b.writeBoolean(v.emergency);
              b.writeDouble(v.speed);
              b.writeDouble(v.available);
              b.writeVarInt(v.powered);
              b.writeVarInt(v.supports);
              b.writeBoolean(v.observing);
              b.writeDouble(v.gearTarget);
            },
            b ->
                new State(
                    b.readBoolean(),
                    b.readBlockPos(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readDouble()));

    @Override
    public Type<State> type() {
      return TYPE;
    }

    static State off() {
      return new State(false, BlockPos.ZERO, -4, 0, false, 0, 0, 0, 0, false, 0);
    }
  }

  public record MenuState(
      int menu, String target, int remainingTenths, String title, int throttlePercent)
      implements CustomPacketPayload {
    static final Type<MenuState> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "driver_menu_state"));
    static final StreamCodec<RegistryFriendlyByteBuf, MenuState> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeVarInt(v.menu);
              b.writeUtf(v.target, 256);
              b.writeVarInt(v.remainingTenths);
              b.writeUtf(v.title, 256);
              b.writeVarInt(v.throttlePercent);
            },
            b ->
                new MenuState(
                    b.readVarInt(),
                    b.readUtf(256),
                    b.readVarInt(),
                    b.readUtf(256),
                    b.readVarInt()));

    @Override
    public Type<MenuState> type() {
      return TYPE;
    }
  }

  public record SetGears(int menu, int[] tenths) implements CustomPacketPayload {
    static final Type<SetGears> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "driver_set_gears"));
    static final StreamCodec<RegistryFriendlyByteBuf, SetGears> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeVarInt(v.menu);
              for (int i = 0; i < DriverModel.MAX_NOTCH; i++) b.writeVarInt(v.tenths[i]);
            },
            b -> {
              int menu = b.readVarInt();
              int[] gears = new int[DriverModel.MAX_NOTCH];
              for (int i = 0; i < gears.length; i++) gears[i] = b.readVarInt();
              return new SetGears(menu, gears);
            });

    @Override
    public Type<SetGears> type() {
      return TYPE;
    }
  }

  public record SchedulePreview(int menu, net.minecraft.world.item.ItemStack item)
      implements CustomPacketPayload {
    static final Type<SchedulePreview> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, "driver_schedule_preview"));
    static final StreamCodec<RegistryFriendlyByteBuf, SchedulePreview> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeVarInt(v.menu);
              net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(b, v.item);
            },
            b ->
                new SchedulePreview(
                    b.readVarInt(),
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));

    @Override
    public Type<SchedulePreview> type() {
      return TYPE;
    }
  }

  static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("2");
    r.playToServer(
        Command.TYPE,
        Command.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            DriverControls.remote(player, message.pos, message.command);
        });
    r.playToClient(State.TYPE, State.CODEC, (message, context) -> clientHandler.accept(message));
    r.playToClient(
        MenuState.TYPE,
        MenuState.CODEC,
        (message, context) -> {
          if (context.player().containerMenu instanceof DriverMenu menu
              && menu.containerId == message.menu) menu.details = message;
        });
    r.playToClient(
        SchedulePreview.TYPE,
        SchedulePreview.CODEC,
        (message, context) -> {
          if (context.player().containerMenu instanceof DriverMenu menu
              && menu.containerId == message.menu) menu.schedulePreview = message.item.copy();
        });
    r.playToServer(
        SetGears.TYPE,
        SetGears.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player
              && player.containerMenu instanceof DriverMenu menu
              && menu.containerId == message.menu) menu.setGears(player, message.tenths);
        });
  }

  static void sync(DriverConsoleEntity e, boolean active) {
    if (e.operator == null
        || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
    var player = level.getServer().getPlayerList().getPlayer(e.operator);
    if (player == null) return;
    PacketDistributor.sendToPlayer(
        player,
        new State(
            active,
            e.getBlockPos(),
            e.notch,
            e.direction,
            e.emergency,
            e.actual,
            e.available,
            e.powered,
            e.supports,
            e.observing,
            e.manualCeiling(e.notch, e.available)));
  }
}
