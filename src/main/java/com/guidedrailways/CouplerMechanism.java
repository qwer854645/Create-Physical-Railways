package com.guidedrailways;
/** Game-scale profiles based on the mechanism, not real-world certified dimensions or force ratings. */
public final class CouplerMechanism {
    public record Profile(int ticks,int releaseTicks,boolean tensionOnly,double slack,double reach,double maxSpeed,double lockAt){}
    public static Profile profile(CouplerKind kind){return switch(kind){
        case STANDARD->new Profile(0,4,false,.1,2,2,0);
        case LINK,LINKLESS->new Profile(20,16,true,1/16.0,.75,.3,1);
        case KNUCKLE,KNUCKLE_SPLIT->new Profile(12,12,false,1/32.0,.45,2,1);
        case SCREWLINK->new Profile(40,28,true,.025,.9,.3,.35);
    };}
    public static double progress(double elapsed,int duration){if(duration<=0)return 1;double t=Math.clamp(elapsed/duration,0,1);return t*t*(3-2*t);}
    public static boolean carries(CouplerKind kind,double progress){return progress>=profile(kind).lockAt();}
    public static double workingLength(CouplerKind kind,double rest,double progress){return rest+(kind==CouplerKind.SCREWLINK?.15*(1-progress):0);}
    public record Motion(double jawOpen,double pinLift,double linkInsertion,double screwTurns){}
    public static Motion motion(CouplerKind kind,double progress){
        double p=Math.clamp(progress,0,1);
        return new Motion(1-Math.clamp(p/.7,0,1),p<.25?p/.25:p<.65?1:(1-p)/.35,
                Math.clamp((p-.25)/.35,0,1),2*Math.clamp((p-.35)/.65,0,1));
    }
    public static double tensionDamping(double extension,double radialSpeed,double stiffness,double damping){
        if(extension<=0)return 0;return radialSpeed<0?Math.min(damping,stiffness*extension/-radialSpeed):damping;
    }
    public static double nominalLength(CouplerKind kind,double geometryLength){return switch(kind){case LINK,LINKLESS->.375;case SCREWLINK->.65;case KNUCKLE,KNUCKLE_SPLIT->18/16.0;default->geometryLength;};}
    public static double lower(CouplerKind kind,double rest){return profile(kind).tensionOnly()?0:Math.max(0,rest-profile(kind).slack());}
    public static double upper(CouplerKind kind,double rest){return rest+profile(kind).slack();}
    public static boolean canCapture(CouplerKind kind,double distance,double radialSpeed,double rest){
        var p=profile(kind);return Double.isFinite(distance)&&Double.isFinite(radialSpeed)&&Math.abs(radialSpeed)<=.25
            &&distance>= (p.tensionOnly()?0:rest-p.slack()*.8)&&distance<=rest+p.slack()*.8;
    }
}
