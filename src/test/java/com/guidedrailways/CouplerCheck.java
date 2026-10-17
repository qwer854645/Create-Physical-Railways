package com.guidedrailways;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.*;
public final class CouplerCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable r,String message){try{r.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        var a=new Vector3d(0,0,0);var b=new Vector3d(0,0,.4);var forward=new Vector3d(0,0,1);var backward=new Vector3d(0,0,-1);var up=new Vector3d(0,1,0);
        var engagement=CouplerGeometry.engage(a,forward,up,b,backward,up);
        check(engagement.point().distance(new Vector3d(0,0,.2))<1e-12,"engagement uses a common midpoint without moving either body");
        check(new Vector3d(1,0,0).rotate(engagement.frame()).distance(forward)<1e-12,"joint X follows coupler longitudinal direction");
        check(a.equals(new Vector3d())&&b.equals(new Vector3d(0,0,.4)),"planning preserves head positions");
        for(int i=0;i<60;i++){
            var global=new Quaterniond().rotateYXZ(i*.13,.15*Math.sin(i),.08*Math.cos(i));
            var offset=new Vector3d(120+i,32,-15);
            var wa=new Vector3d(a).rotate(global).add(offset);var wb=new Vector3d(b).rotate(global).add(offset);
            var faceA=new Vector3d(forward).rotate(global);var faceB=new Vector3d(backward).rotate(global);var worldUp=new Vector3d(up).rotate(global);
            var plan=CouplerGeometry.engage(wa,faceA,worldUp,wb,faceB,worldUp);
            check(plan.point().distance(new Vector3d(wa).add(wb).mul(.5))<1e-10,"engagement invariant under a global rigid transform");
            for(var bodyRot:List.of(new Quaterniond().rotateY(.3+i*.02),new Quaterniond().rotateY(2.7+i*.01))){
                var position=new Vector3d(112,31,-12);var reference=new Vector3d(16000000.5,.5,16000000.5);
                var local=new Vector3d(plan.point()).sub(position).rotate(new Quaterniond(bodyRot).conjugate()).add(reference);
                var storedFrame=new Quaterniond(bodyRot).conjugate().mul(plan.frame());
                var restored=new Vector3d(local).sub(reference).rotate(bodyRot).add(position);
                check(restored.distance(plan.point())<1e-7,"saved plot-local anchor restores the same world point");
                check(Math.abs(new Quaterniond(bodyRot).mul(storedFrame).dot(plan.frame()))>1-1e-12,"saved local frame restores the same world orientation");
            }
        }
        check(CouplerGeometry.engage(a,forward,up,new Vector3d(0,0,.6),backward,up).point().isFinite(),"near maximum engagement gap is valid");
        rejects(()->CouplerGeometry.engage(a,forward,up,b,forward,up),"same-facing couplers must be rejected");
        rejects(()->CouplerGeometry.engage(a,forward,up,new Vector3d(0,0,.61),backward,up),"distant heads must be rejected");
        rejects(()->CouplerGeometry.engage(a,forward,up,new Vector3d(.31,0,.1),backward,up),"sideways offset must be rejected");
        rejects(()->CouplerGeometry.engage(a,forward,up,new Vector3d(0,.26,.1),backward,up),"height mismatch must be rejected");
        rejects(()->CouplerGeometry.engage(a,forward,up,new Vector3d(0,0,-.2),backward,up),"crossed heads must be rejected");
        rejects(()->CouplerGeometry.engage(a,forward,up,b,backward,new Vector3d(0,-1,0)),"opposed carriage up directions must be rejected");
        for(double ma:new double[]{4,20,100,1000})for(double mb:new double[]{4,20,100,1000}){
            var spring=CouplerGeometry.spring(ma,mb);double reduced=1/(1/ma+1/mb);
            check(Math.abs(spring.damping()*spring.damping()/(4*spring.stiffness()*reduced)-1)<1e-12,"native spring uses critical damping for supported reduced mass");
            check(spring.maxForce()>spring.stiffness()*CouplerGeometry.HALF_STROKE,"force budget covers the full buffer stroke");
            check(spring.equals(CouplerGeometry.spring(mb,ma)),"mass tuning is symmetric between ends");
        }
        rejects(()->CouplerGeometry.spring(0,100),"invalid masses rejected");
        var links=new CouplingLinks();var ids=new ArrayList<UUID>();var endpoints=new ArrayList<UUID>();
        for(int i=0;i<50;i++){
            var id=UUID.randomUUID();var ea=UUID.randomUUID();var eb=UUID.randomUUID();ids.add(id);endpoints.add(ea);endpoints.add(eb);
            links.add(new CouplingLinks.Link(id,ea,eb));check(links.forEndpoint(ea).other(ea).equals(eb)&&links.forEndpoint(eb).other(eb).equals(ea),"bidirectional stable pairing");
        }
        var snapshot=links.all();var reload=new CouplingLinks();for(var link:snapshot)reload.add(new CouplingLinks.Link(UUID.fromString(link.id().toString()),UUID.fromString(link.a().toString()),UUID.fromString(link.b().toString())));
        for(var e:endpoints)check(links.forEndpoint(e).equals(reload.forEndpoint(e)),"pair identity survives serializable UUID round trip");
        rejects(()->links.add(new CouplingLinks.Link(UUID.randomUUID(),endpoints.get(0),UUID.randomUUID())),"one endpoint cannot be linked twice");
        rejects(()->links.add(new CouplingLinks.Link(UUID.randomUUID(),endpoints.get(0),endpoints.get(0))),"self-pair rejected");
        reload.remove(ids.get(0));check(reload.forEndpoint(endpoints.get(0))==null&&reload.forEndpoint(endpoints.get(1))==null,"disconnect clears both endpoints even without endpoint objects loaded");
        check(reload.all().size()==49&&links.all().size()==50,"unlink affects only the chosen pair");
        check(Math.abs(CouplerGeometry.HALF_STROKE*2-.2)<1e-12,"total bounded draft travel is 0.2 blocks");
        System.out.println("CouplerCheck: "+checks+" assertions passed");
    }
}
