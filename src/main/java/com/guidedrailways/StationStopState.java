package com.guidedrailways;
/** Debounced physical station presence, independent of schedules and fake Create Train objects. */
public final class StationStopState {
    private int stable;private boolean present;
    public boolean tick(double distance,double speed,boolean valid){
        if(!valid||!Double.isFinite(distance)||!Double.isFinite(speed)){stable=0;return present=false;}
        if(present){if(Math.abs(distance)>.5||Math.abs(speed)>.2){stable=0;present=false;}}
        else if(Math.abs(distance)<=.25&&Math.abs(speed)<=.12){if(++stable>=6)present=true;}
        else stable=0;
        return present;
    }
}
