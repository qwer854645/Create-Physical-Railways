package com.guidedrailways;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
public class ComponentBlock extends Block implements IWrenchable {
    ComponentBlock(Properties p){super(p);}
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext c){
        if(c.getPlayer()==null||!c.getPlayer().mayBuild())return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var unit=VehiclePhysics.owner(c.getLevel(),c.getClickedPos());if(unit==null)return InteractionResult.PASS;
        if(!c.getLevel().mayInteract(c.getPlayer(),c.getClickedPos()))return InteractionResult.FAIL;
        VehiclePhysics.dismantle(unit,c.getPlayer());c.getPlayer().swing(c.getHand(),true);return InteractionResult.CONSUME;
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext c){
        if(c.getPlayer()==null||!c.getPlayer().mayBuild()||!c.getLevel().mayInteract(c.getPlayer(),c.getClickedPos()))return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var unit=VehiclePhysics.owner(c.getLevel(),c.getClickedPos());if(unit==null)return InteractionResult.PASS;
        if(unit.detached){
            if(!(c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player))return InteractionResult.FAIL;
            VehiclePhysics.beginRelocate(player,unit);
            c.getPlayer().swing(c.getHand(),true);return InteractionResult.CONSUME;
        }
        unit.mount.braking=!unit.mount.braking;unit.mount.setChanged();unit.mount.sendData();
        c.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal(unit.mount.braking?"制动已启用":"制动已释放"),true);
        c.getPlayer().swing(c.getHand(),true);return InteractionResult.CONSUME;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){
        if(level.isClientSide)return false;var unit=VehiclePhysics.owner(level,pos);if(unit==null)return super.onDestroyedByPlayer(state,level,pos,player,harvest,fluid);
        if(!player.mayBuild()||!level.mayInteract(player,pos))return false;
        if(!player.getAbilities().instabuild&&!player.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES))return false;
        VehiclePhysics.dismantle(unit,player);return true;
    }
}
