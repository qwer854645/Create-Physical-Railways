package com.guidedrailways;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class DriverConsoleBlock extends HorizontalDirectionalBlock
    implements EntityBlock, IWrenchable {
  public static final MapCodec<DriverConsoleBlock> CODEC = simpleCodec(DriverConsoleBlock::new);

  /** Which display the screen shows; see {@link ConsoleDisplay} for the values. */
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty SCREEN =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create(
          "screen", 0, ConsoleDisplay.COUNT - 1);

  /** True for the few ticks after the emergency button is pressed. */
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty PRESSED =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("pressed");

  DriverConsoleBlock(Properties p) {
    super(p);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SCREEN, ConsoleDisplay.IDLE)
            .setValue(PRESSED, false));
  }

  @Override
  protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(FACING, SCREEN, PRESSED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return defaultBlockState()
        .setValue(FACING, c.getHorizontalDirection())
        .setValue(SCREEN, ConsoleDisplay.IDLE)
        .setValue(PRESSED, false);
  }

  @Override
  public void setPlacedBy(
      Level level,
      BlockPos pos,
      BlockState state,
      net.minecraft.world.entity.LivingEntity entity,
      net.minecraft.world.item.ItemStack stack) {
    super.setPlacedBy(level, pos, state, entity, stack);
    if (level instanceof net.minecraft.server.level.ServerLevel server
        && level.getBlockEntity(pos) instanceof DriverConsoleEntity console)
      console.placed(server, entity instanceof Player player ? player : null);
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return box(1, 0, 1, 15, 14, 15);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new DriverConsoleEntity(p, s);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level l, BlockState s, BlockEntityType<T> type) {
    return type == Content.DRIVER_ENTITY.get()
        ? (world, pos, state, be) -> {
          if (world instanceof net.minecraft.server.level.ServerLevel server
              && be instanceof DriverConsoleEntity console)
            {
              DriverControls.register(server, console);
              console.clearPressIfDue();
            }
          else if (be instanceof DriverConsoleEntity console) console.clientTick();
        }
        : null;
  }

  @Override
  protected ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (com.simibubi.create.AllItems.SCHEDULE.isIn(stack)) {
      if (!level.isClientSide && level.getBlockEntity(pos) instanceof DriverConsoleEntity console)
        console.schedule.install(player, stack);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return super.useItemOn(stack, state, level, pos, player, hand, hit);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    if (player instanceof ServerPlayer server
        && l.getBlockEntity(p) instanceof DriverConsoleEntity console) {
      DriverControls.register(server.serverLevel(), console);
      if (player.isShiftKeyDown()) console.open(server);
      else if (!DriverControls.isPrimary(console)) DriverControls.secondaryHint(player);
      else {
        if (!console.active) console.command(player, 0);
        if (console.active && player.getUUID().equals(console.operator))
          DriverNetwork.sync(console, true);
        else if (console.active)
          player.displayClientMessage(
              net.minecraft.network.chat.Component.translatable("gui.guidedrailways.driver.busy"),
              true);
      }
    }
    return InteractionResult.sidedSuccess(l.isClientSide);
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof DriverConsoleEntity console)
      console.schedule.give(player);
    return super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  public InteractionResult onSneakWrenched(
      BlockState state, net.minecraft.world.item.context.UseOnContext context) {
    var player = context.getPlayer();
    if (player == null
        || !player.mayBuild()
        || !context.getLevel().mayInteract(player, context.getClickedPos()))
      return InteractionResult.FAIL;
    if (!context.getLevel().isClientSide
        && context.getLevel().getBlockEntity(context.getClickedPos())
            instanceof DriverConsoleEntity console) console.schedule.give(player);
    return IWrenchable.super.onSneakWrenched(state, context);
  }

  @Override
  public InteractionResult onWrenched(
      BlockState state, net.minecraft.world.item.context.UseOnContext context) {
    if (context.getPlayer() == null
        || !context.getPlayer().mayBuild()
        || !context.getLevel().mayInteract(context.getPlayer(), context.getClickedPos()))
      return InteractionResult.FAIL;
    if (!context.getLevel().isClientSide
        && context.getLevel().getBlockEntity(context.getClickedPos())
            instanceof DriverConsoleEntity e) {
      e.park();
    }
    return IWrenchable.super.onWrenched(state, context);
  }

  @Override
  protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!s.is(next.getBlock())) {
      if (l.getBlockEntity(p) instanceof DriverConsoleEntity console
          && l instanceof net.minecraft.server.level.ServerLevel server)
        DriverControls.removed(server, console);
      super.onRemove(s, l, p, next, moving);
    }
  }
}
