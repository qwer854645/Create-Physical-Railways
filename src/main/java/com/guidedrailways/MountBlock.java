package com.guidedrailways;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public final class MountBlock extends ComponentBlock
    implements IRotate, IBE<MountEntity>, BlockSubLevelAssemblyListener {
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty COMPACT =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("compact");

  private record Transfer(
      UUID bogie,
      boolean brake,
      RailGauge gauge,
      BogeyVariant variant,
      org.joml.Quaterniond worldReference,
      long expires) {}

  private static final Map<GlobalPos, Transfer> TRANSFERS = new HashMap<>();

  MountBlock(Properties p) {
    super(p);
    registerDefaultState(
        stateDefinition.any().setValue(BogieBlock.STYLE, 0).setValue(COMPACT, false));
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<
              net.minecraft.world.level.block.Block, BlockState>
          builder) {
    builder.add(BogieBlock.STYLE, COMPACT);
  }

  private static final VoxelShape TOP = box(0, 15.75, 0, 16, 16, 16);

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return shape(BogieBlock.variant(s), s.getValue(COMPACT));
  }

  static VoxelShape shape(BogeyVariant variant, boolean compact) {
    return MountShapes.get(variant, compact);
  }

  @Override
  protected VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) {
    return TOP;
  }

  @Override
  protected VoxelShape getInteractionShape(BlockState s, BlockGetter l, BlockPos p) {
    return TOP;
  }

  @Override
  protected net.minecraft.world.ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand,
      net.minecraft.world.phys.BlockHitResult hit) {
    if (!level.isClientSide && stack.getItem() instanceof net.minecraft.world.item.BlockItem item) {
      var context =
          new net.minecraft.world.item.context.BlockPlaceContext(player, hand, stack, hit);
      var target = context.getClickedPos();
      var placed = item.getBlock().getStateForPlacement(context);
      boolean replaceable = level.getBlockState(target).canBeReplaced(context);
      boolean survives = placed != null && placed.canSurvive(level, target);
      boolean clear =
          placed != null && level.isUnobstructed(placed, target, CollisionContext.of(player));
      boolean permitted = context.canPlace();
      if (!permitted || !replaceable || !survives || !clear)
        org.slf4j.LoggerFactory.getLogger("guidedrailways")
            .info(
                "[MountPlacement] style={} compact={} face={} item={} target={} canPlace={} replaceable={} survives={} unobstructed={}",
                BogieBlock.variant(state).id,
                state.getValue(COMPACT),
                hit.getDirection(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item),
                target,
                permitted,
                replaceable,
                survives,
                clear);
    }
    return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  static void updateStyle(MountEntity mount) {
    var state = mount.getBlockState();
    if (state.getValue(BogieBlock.STYLE) != mount.variant.ordinal())
      mount
          .getLevel()
          .setBlockAndUpdate(
              mount.getBlockPos(), state.setValue(BogieBlock.STYLE, mount.variant.ordinal()));
  }

  @Override
  public Direction.Axis getRotationAxis(BlockState s) {
    return Direction.Axis.Y;
  }

  @Override
  public boolean hasShaftTowards(LevelReader l, BlockPos p, BlockState s, Direction face) {
    return face == Direction.UP;
  }

  @Override
  public Class<MountEntity> getBlockEntityClass() {
    return MountEntity.class;
  }

  @Override
  public BlockEntityType<? extends MountEntity> getBlockEntityType() {
    return Content.MOUNT_ENTITY.get();
  }

  @Override
  protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
    IBE.onRemove(s, l, p, next);
    super.onRemove(s, l, p, next, moving);
  }

  @Override
  public void beforeMove(ServerLevel from, ServerLevel to, BlockState s, BlockPos a, BlockPos b) {
    if (from.getBlockEntity(a) instanceof MountEntity mount && mount.bogieId != null) {
      org.joml.Quaterniond reference = null;
      if (mount.pivotReferenceKnown
          && dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(from, a)
              instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel carrier) {
        var pose =
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(from)
                .getPipeline()
                .readPose(carrier, new dev.ryanhcode.sable.companion.math.Pose3d());
        reference = new org.joml.Quaterniond(pose.orientation()).mul(mount.pivotReference);
      }
      TRANSFERS.put(
          GlobalPos.of(to.dimension(), b),
          new Transfer(
              mount.bogieId,
              mount.braking,
              mount.gauge,
              mount.variant,
              reference,
              to.getGameTime() + 20));
    }
  }

  @Override
  public void afterMove(ServerLevel from, ServerLevel to, BlockState s, BlockPos a, BlockPos b) {
    if (to.getBlockEntity(b) instanceof MountEntity mount) {
      restore(mount);
      VehiclePhysics.register(mount);
    }
  }

  static void restore(MountEntity mount) {
    if (!(mount.getLevel() instanceof ServerLevel level)) return;
    TRANSFERS
        .entrySet()
        .removeIf(
            e ->
                e.getKey().dimension().equals(level.dimension())
                    && e.getValue().expires < level.getGameTime());
    var key = GlobalPos.of(level.dimension(), mount.getBlockPos());
    var t = TRANSFERS.get(key);
    if (t != null) {
      if (t.worldReference != null) {
        if (!(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(
                level, mount.getBlockPos())
            instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel carrier)) return;
        var pose =
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level)
                .getPipeline()
                .readPose(carrier, new dev.ryanhcode.sable.companion.math.Pose3d());
        mount.pivotReference.set(
            BogieYawModel.transfer(
                new org.joml.Quaterniond(), t.worldReference, pose.orientation()));
        mount.pivotReferenceKnown = true;
      }
      mount.bogieId = t.bogie;
      mount.braking = t.brake;
      mount.gauge = t.gauge;
      mount.variant = t.variant;
      mount.setChanged();
      TRANSFERS.remove(key);
    }
  }

  static void clear(ServerLevel level) {
    TRANSFERS.keySet().removeIf(p -> p.dimension().equals(level.dimension()));
  }
}
