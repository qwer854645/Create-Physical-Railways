package com.guidedrailways;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/** Uses the installed mod's animated model and textures, without copying its assets. */
final class SteamRailwaysClient {
    static void mono(CompoundTag data,float angle,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        CRBogeyStyles.MONOBOGEY.render(BogeySizes.SMALL,partial,pose,buffers,light,overlay,angle,data,false);
    }
}
