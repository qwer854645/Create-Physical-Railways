package com.guidedrailways;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.HashMap;
final class BogieCollisionShape {
    private static final Map<RailGauge,Map<Integer,VoxelShape>> PROFILES=new java.util.EnumMap<>(RailGauge.class);
    static{for(var gauge:RailGauge.values())PROFILES.put(gauge,build(gauge));}
    static VoxelShape get(int part){return get(part,RailGauge.STANDARD);}
    static VoxelShape get(int part,RailGauge gauge){return PROFILES.get(gauge).getOrDefault(part,Shapes.empty());}
    private static Map<Integer,VoxelShape> build(RailGauge gauge){
        var result=new HashMap<Integer,VoxelShape>();int pieces=0;
        for(var cell:BogieCollision.cells(gauge)){
            var shape=Shapes.empty();for(var b:cell.boxes())shape=Shapes.or(shape,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
            pieces+=shape.toAabbs().size();result.put(cell.part(),shape);
        }
        org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[BogieCollision] sourceBoxes={} occupiedCells={} voxelBoxes={} layout=cell_partitioned",BogieCollision.BOXES.size(),result.size(),pieces);
        return Map.copyOf(result);
    }
}
