package com.guidedrailways;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.HashMap;
final class BogieCollisionShape {
    private static final Map<Integer,VoxelShape> SHAPES=build();
    private static final Map<Integer,VoxelShape> NATIVE_SHAPES=build(BogieCollision.NATIVE_CELLS);
    static VoxelShape get(int part){return SHAPES.getOrDefault(part,Shapes.empty());}
    static VoxelShape getNative(int part){return NATIVE_SHAPES.getOrDefault(part,Shapes.empty());}
    private static Map<Integer,VoxelShape> build(){
        return build(BogieCollision.CELLS);
    }
    private static Map<Integer,VoxelShape> build(java.util.List<BogieCollision.Cell> cells){
        var result=new HashMap<Integer,VoxelShape>();int pieces=0;
        for(var cell:cells){
            var shape=Shapes.empty();for(var b:cell.boxes())shape=Shapes.or(shape,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
            pieces+=shape.toAabbs().size();result.put(cell.part(),shape);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieCollision] sourceBoxes={} occupiedCells={} voxelBoxes={} layout={}",BogieCollision.BOXES.size(),result.size(),pieces,cells==BogieCollision.NATIVE_CELLS?"solid_native_web":"detailed_entity");
        return Map.copyOf(result);
    }
}
