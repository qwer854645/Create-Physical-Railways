package com.guidedrailways;
/** Inelastic contact with one body constrained to a rail direction. All velocities are metres/second. */
public final class NativeContactModel {
    private NativeContactModel(){}
    public static double impulse(double closing,double physicalMass,double trainMass,double alignment){
        if(!Double.isFinite(closing)||!Double.isFinite(physicalMass)||!Double.isFinite(trainMass)||!Double.isFinite(alignment)
                ||closing<=0||physicalMass<=0||trainMass<=0||Math.abs(alignment)>1.000001)return 0;
        return closing/(1/physicalMass+alignment*alignment/trainMass);
    }
}
