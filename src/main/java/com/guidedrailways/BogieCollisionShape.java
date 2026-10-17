package com.guidedrailways;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
final class BogieCollisionShape {
    static final VoxelShape SHAPE=build();
    private static VoxelShape build(){
        var shape=Shapes.empty();for(var b:BogieCollision.BOXES)shape=Shapes.or(shape,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieCollision] sourceBoxes={} voxelBoxes={}",BogieCollision.BOXES.size(),shape.toAabbs().size());return shape;
    }
}
