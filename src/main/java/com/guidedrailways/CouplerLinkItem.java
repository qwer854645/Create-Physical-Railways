package com.guidedrailways;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public final class CouplerLinkItem extends Item {
  CouplerLinkItem(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, java.util.List<Component> text, TooltipFlag flag) {
    UserText.tooltip(text, "tooltip.guidedrailways.coupler_link");
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var endpoint = CouplerEndpoint.get(context.getLevel().getBlockEntity(context.getClickedPos()));
    if (endpoint == null) return InteractionResult.PASS;
    var player = context.getPlayer();
    if (player == null
        || !player.mayBuild()
        || !context.getLevel().mayInteract(player, context.getClickedPos()))
      return InteractionResult.FAIL;
    if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
    if (!(player instanceof net.minecraft.server.level.ServerPlayer server))
      return InteractionResult.FAIL;
    try {
      endpoint.link(server);
      server.swing(context.getHand(), true);
      return InteractionResult.CONSUME;
    } catch (IllegalArgumentException | IllegalStateException error) {
      server.displayClientMessage(UserText.error(error), true);
      return InteractionResult.FAIL;
    }
  }
}
