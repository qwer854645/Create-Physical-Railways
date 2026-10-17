package com.guidedrailways;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.HashMap;
final class BogieCollisionShape {
    private static final Map<Integer,VoxelShape> SHAPES=build();
    static VoxelShape get(int part){return SHAPES.getOrDefault(part,Shapes.empty());}
    private static Map<Integer,VoxelShape> build(){
        var result=new HashMap<Integer,VoxelShape>();int pieces=0;
        for(var cell:BogieCollision.CELLS){
            var shape=Shapes.empty();for(var b:cell.boxes())shape=Shapes.or(shape,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
            pieces+=shape.toAabbs().size();result.put(cell.part(),shape);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieCollision] sourceBoxes={} occupiedCells={} voxelBoxes={} layout=cell_partitioned",BogieCollision.BOXES.size(),result.size(),pieces);
        return Map.copyOf(result);
    }
}
