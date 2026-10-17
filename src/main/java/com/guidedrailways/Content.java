package com.guidedrailways;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
public final class Content {
    static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(GuidedRailways.ID);
    static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(GuidedRailways.ID);
    static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,GuidedRailways.ID);
    static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,GuidedRailways.ID);
    static BlockBehaviour.Properties metal(){return BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion();}
    public static final DeferredBlock<MountBlock> MOUNT=BLOCKS.register("bogie_mount",()->new MountBlock(metal()));
    public static final DeferredBlock<CollisionPartBlock> COLLISION=BLOCKS.register("bogie_collision",()->new CollisionPartBlock(metal()));
    public static final DeferredBlock<BogieBlock> BOGIE=BLOCKS.register("bogie_frame",()->new BogieBlock(metal()));
    public static final DeferredBlock<CouplerBlock> COUPLER=BLOCKS.register("coupler",()->new CouplerBlock(metal()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MountEntity>> MOUNT_ENTITY=ENTITIES.register("bogie_mount",()->BlockEntityType.Builder.of(MountEntity::new,MOUNT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<BogieEntity>> BOGIE_ENTITY=ENTITIES.register("bogie_frame",()->BlockEntityType.Builder.of(BogieEntity::new,BOGIE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CouplerEntity>> COUPLER_ENTITY=ENTITIES.register("coupler",()->BlockEntityType.Builder.of(CouplerEntity::new,COUPLER.get()).build(null));
    public static final DeferredItem<BogieItem> BOGIE_ITEM=ITEMS.register("bogie",()->new BogieItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> COUPLER_ITEM=ITEMS.register("coupler",()->new BlockItem(COUPLER.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack s,TooltipContext c,java.util.List<Component> text,net.minecraft.world.item.TooltipFlag f){text.add(Component.translatable("tooltip.guidedrailways.coupler"));}
    });
    public static final DeferredItem<CouplingToolItem> COUPLING_TOOL=ITEMS.register("coupling_tool",()->new CouplingToolItem(new Item.Properties().stacksTo(1)));
    static {TABS.register("main",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.guidedrailways")).icon(()->new ItemStack(BOGIE_ITEM.get())).displayItems((p,o)->{o.accept(BOGIE_ITEM.get());o.accept(COUPLER_ITEM.get());}).build());}
    static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);
        if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)TrainClient.register(bus);
    }
}
