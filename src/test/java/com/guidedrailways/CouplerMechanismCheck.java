package com.guidedrailways;
public final class CouplerMechanismCheck {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(var kind:CouplerKind.values()){
            var profile=CouplerMechanism.profile(kind);double previous=-1;
            for(int tick=-2;tick<=profile.ticks()+2;tick++){
                double progress=CouplerMechanism.progress(tick,profile.ticks());
                check(progress>=0&&progress<=1&&progress>=previous,"saved operation time produces bounded monotonic progress");previous=progress;
                var motion=CouplerMechanism.motion(kind,progress);
                check(motion.jawOpen()>=0&&motion.jawOpen()<=1&&motion.pinLift()>=0&&motion.pinLift()<=1,"moving native parts have bounded travel");
                if(kind==CouplerKind.LINK||kind==CouplerKind.LINKLESS){
                    if(motion.linkInsertion()>0&&motion.linkInsertion()<1)check(Math.abs(motion.pinLift()-1)<1e-9,"link enters only while the pin is fully lifted");
                    if(CouplerMechanism.carries(kind,progress))check(motion.pinLift()<1e-9&&motion.linkInsertion()==1,"link takes load only after pin seats");
                }
                if(kind==CouplerKind.KNUCKLE||kind==CouplerKind.KNUCKLE_SPLIT)if(CouplerMechanism.carries(kind,progress))check(motion.jawOpen()==0,"automatic coupler cannot lock an open knuckle");
                if(kind==CouplerKind.SCREWLINK&&progress<.35)check(motion.screwTurns()==0&&!CouplerMechanism.carries(kind,progress),"screw cannot tighten or carry load before the link is hung");
            }
            check(CouplerMechanism.carries(kind,1),"completed operations can carry load");
            check(CouplerMechanism.lower(kind,1)<=1&&CouplerMechanism.upper(kind,1)>1,"joint range spans its nominal length");
        }
        check(CouplerMechanism.profile(CouplerKind.LINK).tensionOnly()&&CouplerMechanism.profile(CouplerKind.SCREWLINK).tensionOnly(),"chain mechanisms cannot transmit compression through a radial spring");
        check(!CouplerMechanism.profile(CouplerKind.KNUCKLE).tensionOnly(),"closed automatic coupler has draft and buff response");
        for(double extension:new double[]{.001,.01,.1,1})for(double velocity:new double[]{-100,-1,-.01,0,.1,10}){
            double k=200,c=40,d=CouplerMechanism.tensionDamping(extension,velocity,k,c);
            check(k*extension+d*velocity>=-1e-8,"closing damping never produces a pushing force in a tension-only connection");
            check(d>=0&&d<=c,"damping is bounded and dissipative");
        }
        check(CouplerMechanism.tensionDamping(-.1,1,200,40)==0,"a slack link has no damping force");
        check(CouplerMechanism.workingLength(CouplerKind.SCREWLINK,.65,0)>CouplerMechanism.workingLength(CouplerKind.SCREWLINK,.65,1),"turnbuckle progressively takes up installation slack");
        System.out.println("CouplerMechanismCheck: "+checks+" assertions passed");
    }
}
