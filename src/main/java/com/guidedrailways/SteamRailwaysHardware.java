package com.guidedrailways;
import com.railwayteam.railways.content.buffer.single_deco.LinkPinBlock;
import com.railwayteam.railways.content.buffer.headstock.HeadstockBlock;
import com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
final class SteamRailwaysHardware implements RailwayHardware.Bridge {
    @Override public CouplerKind kind(BlockState state){
        if(state.getBlock() instanceof LinkPinBlock)return CouplerKind.railway(state.getValue(LinkPinBlock.STYLE).getSerializedName());
        if(!(state.getBlock() instanceof HeadstockBlock||state.getBlock() instanceof CopycatHeadstockBlock))return null;
        String style=state.getValue(HeadstockBlock.STYLE).getSerializedName();
        return CouplerKind.railway(style);
    }
    private Vector3d point(CouplerEndpoint endpoint,boolean front){
        var state=endpoint.getBlockState();var facing=state.getValue(CouplerBlock.FACING);
        var shape=state.getShape(endpoint.getLevel(),endpoint.getBlockPos());
        if(shape.isEmpty())return new Vector3d(.5,.5,.5);
        var box=shape.bounds();double x=(box.minX+box.maxX)*.5,y=(box.minY+box.maxY)*.5,z=(box.minZ+box.maxZ)*.5;
        if(facing.getStepX()!=0)x=(facing.getStepX()>0==front?box.maxX:box.minX);
        else z=(facing.getStepZ()>0==front?box.maxZ:box.minZ);
        if(!front){x+=facing.getStepX()*.08;z+=facing.getStepZ()*.08;}
        return new Vector3d(x,y,z);
    }
    @Override public Vector3d pivot(CouplerEndpoint endpoint){return point(endpoint,false);}
    @Override public Vector3d tip(CouplerEndpoint endpoint){return point(endpoint,true);}
}
