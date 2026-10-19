package com.guidedrailways;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.*;

/** Bridges genuine Rapier contacts back to Create's rail-constrained train speed, without anticipatory braking. */
public final class NativeTrainContacts {
    private record Physical(ServerSubLevel body,double mass,Vector3d velocity){}
    private record Native(Train train,double mass,Vector3d axis){}
    private record Pair(int body,int carriage){}
    private record Contact(Physical body,Native carriage,Vector3d normal,double force){}
    public static final class Frame {
        private final ServerLevel level;
        private final Map<Integer,Physical> bodies=new HashMap<>();
        private final Map<Integer,Native> carriages=new HashMap<>();
        private Frame(ServerLevel level){this.level=level;}
        public void accept(double[] data){
            // Sable 2.0.3: IDs, contact force, two local normals, two local points (15 doubles).
            if(data.length%15!=0)return;
            var pairs=new LinkedHashMap<Pair,Contact>();
            for(int i=0;i<data.length;i+=15){
                int a=(int)data[i],b=(int)data[i+1];boolean direct=bodies.containsKey(a)&&carriages.containsKey(b);
                var body=bodies.get(direct?a:b);var carriage=carriages.get(direct?b:a);
                if(body==null||carriage==null||body.body.isRemoved()||!Double.isFinite(data[i+2])||data[i+2]<=0)continue;
                int offset=direct?3:6;var n=new Vector3d(data[i+offset],data[i+offset+1],data[i+offset+2]);
                n.rotate(body.body.logicalPose().orientation());if(!n.isFinite()||n.lengthSquared()<1e-12)continue;n.normalize();
                var key=new Pair(direct?a:b,direct?b:a);var old=pairs.get(key);
                // Native reports the whole manifold force for every point. Never count it once per voxel/point.
                if(old==null||data[i+2]>old.force)pairs.put(key,new Contact(body,carriage,n,data[i+2]));
            }
            for(var contact:pairs.values()){
                var p=contact.body;var c=contact.carriage;var n=contact.normal;
                double before=c.train.speed;
                double closing=new Vector3d(p.velocity).fma(-before*20,c.axis).dot(n);
                double alignment=n.dot(c.axis),impulse=NativeContactModel.impulse(closing,p.mass,c.mass,alignment);
                if(!(impulse>0))continue;
                c.train.speed+=impulse*alignment/c.mass/20;
                // Virtual response avoids counting the same incoming momentum again against another carriage.
                // Sable already applied the physical body's real contact response; no second impulse is added.
                p.velocity.fma(-impulse/p.mass,n);
                org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[NativeTrainContact] body={} train={} closing={} impulse={} nativeSpeedBefore={} nativeSpeedAfter={} physicalMass={} trainMass={}",p.body.getUniqueId(),c.train.id,closing,impulse,before*20,c.train.speed*20,p.mass,c.mass);
            }
        }
    }
    public static Frame capture(ServerLevel level,Map<Integer,KinematicContraption> contraptions){
        var frame=new Frame(level);var system=SubLevelPhysicsSystem.get(level);if(system==null)return frame;
        for(var unit:VehiclePhysics.cargoUnits(level))for(var body:List.of(unit.bogie,unit.carrier))if(!body.isRemoved()){
            double mass=body.getMassTracker().getMass();if(mass>0&&Double.isFinite(mass))frame.bodies.putIfAbsent(body.getRuntimeId(),new Physical(body,mass,system.getPipeline().getLinearVelocity(body,new Vector3d())));
        }
        if(frame.bodies.isEmpty())return frame;
        var masses=new IdentityHashMap<Train,Double>();
        for(var k:contraptions.values())if(k instanceof CarriageContraptionEntity entity&&!entity.isRemoved()&&entity.getCarriage()!=null){
            double mass=k.sable$getMassTracker().getMass();if(mass>0&&Double.isFinite(mass))masses.merge(entity.getCarriage().train,mass,Double::sum);
        }
        for(var entry:contraptions.entrySet())if(entry.getValue() instanceof CarriageContraptionEntity entity&&!entity.isRemoved()){
            var carriage=entity.getCarriage();if(carriage==null||carriage.train==null||carriage.train.graph==null)continue;
            var train=carriage.train;var front=carriage.getLeadingPoint();var rear=carriage.getTrailingPoint();
            if(front.node1==null||rear.node1==null||front.edge==null||rear.edge==null)continue;
            var f=front.getPosition(train.graph);var r=rear.getPosition(train.graph);var axis=new Vector3d(f.x-r.x,f.y-r.y,f.z-r.z);
            double mass=masses.getOrDefault(train,0.0);if(mass<=0||!axis.isFinite()||axis.lengthSquared()<1e-10)continue;
            frame.carriages.put(entry.getKey(),new Native(train,mass,axis.normalize()));
        }
        return frame;
    }
}
