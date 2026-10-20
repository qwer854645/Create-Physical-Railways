package com.guidedrailways;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class BogieItem extends Item {
  BogieItem(Properties p) {
    super(p);
  }

  @Override
  public String getDescriptionId() {
    return "item.guidedrailways.bogie";
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, java.util.List<Component> text, TooltipFlag f) {
    UserText.tooltip(text, "tooltip.guidedrailways.bogie");
    if (PhysicalBogeySelection.tooltip != null) PhysicalBogeySelection.tooltip.accept(text);
  }

  @Override
  public net.minecraft.world.InteractionResultHolder<ItemStack> use(
      net.minecraft.world.level.Level level,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand) {
    if (menu(player)) {
      if (level.isClientSide && PhysicalBogeySelection.menu != null)
        PhysicalBogeySelection.menu.run();
      return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    if (level.isClientSide)
      return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
    try {
      VehiclePlacement.placeAimed((net.minecraft.server.level.ServerPlayer) player);
      if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
      player.swing(hand, true);
      return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    } catch (IllegalArgumentException | IllegalStateException error) {
      rejected(error);
      player.displayClientMessage(UserText.error(error), true);
      return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
    }
  }

  @Override
  public InteractionResult useOn(UseOnContext c) {
    if (c.getPlayer() != null && menu(c.getPlayer())) {
      if (c.getLevel().isClientSide && PhysicalBogeySelection.menu != null)
        PhysicalBogeySelection.menu.run();
      return InteractionResult.SUCCESS;
    }
    if (c.getPlayer() == null
        || !c.getPlayer().mayBuild()
        || !c.getLevel().mayInteract(c.getPlayer(), c.getClickedPos()))
      return InteractionResult.FAIL;
    if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
    try {
      VehiclePlacement.place((ServerLevel) c.getLevel(), c);
      if (!c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
      c.getPlayer().swing(c.getHand(), true);
      return InteractionResult.CONSUME;
    } catch (IllegalArgumentException | IllegalStateException error) {
      rejected(error);
      c.getPlayer().displayClientMessage(UserText.error(error), true);
      return InteractionResult.FAIL;
    }
  }

  private static boolean menu(net.minecraft.world.entity.player.Player player) {
    return player.isShiftKeyDown() && net.neoforged.fml.ModList.get().isLoaded("create_bb");
  }

  private static void rejected(RuntimeException error) {
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info("[BogeyPlacement] rejected reason={}", error.getMessage());
  }
}
