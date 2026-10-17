package com.guidedrailways;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
public final class CouplingToolItem extends Item {
    CouplingToolItem(Properties p){super(p);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> text,TooltipFlag flag){text.add(Component.translatable("tooltip.guidedrailways.coupling_tool"));}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof CouplerEntity e))return InteractionResult.PASS;
        var player=c.getPlayer();if(player==null||!player.mayBuild()||!c.getLevel().mayInteract(player,c.getClickedPos()))return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        try{CouplerConnections.tool((ServerLevel)c.getLevel(),e,(ServerPlayer)player,player.isShiftKeyDown());player.swing(c.getHand(),true);return InteractionResult.CONSUME;}
        catch(IllegalArgumentException|IllegalStateException error){player.displayClientMessage(Component.literal(error.getMessage()),true);return InteractionResult.FAIL;}
    }
}
