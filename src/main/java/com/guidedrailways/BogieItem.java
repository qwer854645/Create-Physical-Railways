package com.guidedrailways;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
public final class BogieItem extends Item {
    BogieItem(Properties p){super(p);}
    @Override public void appendHoverText(ItemStack s,TooltipContext c,java.util.List<Component> text,TooltipFlag f){text.add(Component.translatable("tooltip.guidedrailways.bogie"));}
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        if(level.isClientSide)return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
        try{
            VehiclePhysics.placeAimed((net.minecraft.server.level.ServerPlayer)player);
            if(!player.getAbilities().instabuild)player.getItemInHand(hand).shrink(1);player.swing(hand,true);
            return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
        }catch(IllegalArgumentException|IllegalStateException error){player.displayClientMessage(Component.literal(error.getMessage()),true);return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));}
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getPlayer()==null||!c.getPlayer().mayBuild()||!c.getLevel().mayInteract(c.getPlayer(),c.getClickedPos()))return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        try{VehiclePhysics.place((ServerLevel)c.getLevel(),c);if(!c.getPlayer().getAbilities().instabuild)c.getItemInHand().shrink(1);c.getPlayer().swing(c.getHand(),true);return InteractionResult.CONSUME;}
        catch(IllegalArgumentException|IllegalStateException error){c.getPlayer().displayClientMessage(Component.literal(error.getMessage()),true);return InteractionResult.FAIL;}
    }
}
