package com.guidedrailways;
import java.util.UUID;
/** Uses actual Minecraft NBT classes, without creating a world or a server. */
public final class CouplerPersistenceCheck {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        // Exercise the actual disk serializer, not the richer update-packet helper.
        var saved=new CouplerEndpoint(null);saved.endpointId=UUID.randomUUID();saved.hasGeometry=true;
        saved.anchor.set(.5,.375,.875);saved.frame.rotationY(.4);saved.restLength=1.125;
        saved.visualConnected=true;saved.visualPeerId=UUID.randomUUID();saved.visualPeerPos=new net.minecraft.core.BlockPos(1280001,64,1280003);
        saved.operationStart=300;saved.operationTicks=12;saved.operationFrom=.2;saved.operationTo=1;
        var disk=new net.minecraft.nbt.CompoundTag();saved.write(disk);
        var restored=new CouplerEndpoint(null);restored.read(disk);
        check(restored.endpointId.equals(saved.endpointId)&&restored.hasGeometry,"disk restore retains the endpoint identity used by saved pair lookup");
        check(restored.visualConnected&&restored.visualPeerId.equals(saved.visualPeerId)&&restored.visualPeerPos.equals(saved.visualPeerPos),"disk restore retains peer metadata so re-registration does not restart an established mechanism");
        check(restored.anchor.distance(saved.anchor)<1e-12&&restored.frame.equals(saved.frame,1e-12)&&restored.restLength==saved.restLength,"physical socket and standard length survive disk serialization");
        check(restored.operationStart==saved.operationStart&&restored.operationTicks==12&&restored.operationFrom==.2&&restored.operationTo==1,"an unfinished coupling operation retains its original timeline");
        disk.putInt("CouplerVersion",5);restored.read(disk);
        check(!restored.hasGeometry,"unsupported historical socket formats are rejected rather than migrated");
        System.out.println("CouplerPersistenceCheck: "+checks+" assertions passed");
    }
}
