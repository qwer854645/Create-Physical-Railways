package com.guidedrailways.mixin;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import java.util.*;

/** Missing optional mods or changed public APIs disable only the corresponding hook. */
public final class CargoCompatPlugin implements IMixinConfigPlugin {
    private static final Map<String,Set<String>> METHODS=Map.of(
        "DockCargoTankMixin",Set.of("insert(Ldev/simulated_team/simulated/multiloader/tanks/CFluidType;JZLjava/lang/Runnable;)J","extract(Ldev/simulated_team/simulated/multiloader/tanks/CFluidType;JZLjava/lang/Runnable;)J"),
        "DockCargoItemMixin",Set.of("insertGeneral(Ldev/simulated_team/simulated/multiloader/inventory/ItemInfoWrapper;IZ)I","extractGeneral(Ldev/simulated_team/simulated/multiloader/inventory/ItemInfoWrapper;IZ)I","insertSlot(Lnet/minecraft/world/item/ItemStack;IZ)Lnet/minecraft/world/item/ItemStack;","extractSlot(IIZ)Lnet/minecraft/world/item/ItemStack;"),
        "DockCargoBindingMixin",Set.of("initialize()V"),
        "HoseCargoBindingMixin",Set.of("onLoad()V"),
        "LogisticsSnapshotMixin",Set.of("capture(Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/sprocketgames/create_aeronautics_automated_logistics/cargo/LinkedCargoSnapshot;","empty()Lnet/sprocketgames/create_aeronautics_automated_logistics/cargo/LinkedCargoSnapshot;"),
        "LogisticsDockTransferMixin",Set.of("captureConnector(Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;)Lnet/sprocketgames/create_aeronautics_automated_logistics/dock/DockTransferSnapshot$ConnectorContents;","handoffLocalItem(Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;)I"));
    @Override public boolean shouldApplyMixin(String target,String mixin){
        var expected=METHODS.get(mixin.substring(mixin.lastIndexOf('.')+1));if(expected==null)return true;
        try{
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            var methods=new HashSet<String>();for(var method:node.methods)methods.add(method.name+method.desc);
            if(methods.containsAll(expected))return true;
            org.slf4j.LoggerFactory.getLogger("guidedrailways").warn("Cargo compatibility hook disabled: {} has an unsupported API",target);
        }catch(ClassNotFoundException|java.io.IOException ignored){}
        com.guidedrailways.CargoHookStatus.disable(target.startsWith("com.mk2525.")?"hose":target.startsWith("dev.simulated_team.")?"dock":"logistics");
        return false;
    }
    @Override public void onLoad(String p){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
