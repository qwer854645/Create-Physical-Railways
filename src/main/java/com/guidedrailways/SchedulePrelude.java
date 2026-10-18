package com.guidedrailways;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
/** Executes non-travel entries in order with a strict per-tick budget. */
public final class SchedulePrelude {
    public record Result(int entry,int executed,boolean finished){}
    public static Result run(int size,boolean cyclic,int start,int budget,IntPredicate destination,IntConsumer apply){
        int entry=Math.clamp(start,0,Math.max(0,size)),executed=0;
        if(size<=0)return new Result(0,0,true);
        if(entry==size){if(!cyclic)return new Result(entry,0,true);entry=0;}
        while(executed<budget&&!destination.test(entry)){
            apply.accept(entry);executed++;entry++;
            if(entry==size){if(!cyclic)return new Result(entry,executed,true);entry=0;}
        }
        return new Result(entry,executed,false);
    }
    public static double throttle(double value){return Double.isFinite(value)?Math.clamp(value,.05,1):1;}
    public static double ceiling(double available,double throttle){return Double.isFinite(available)?Math.max(0,available)*throttle(throttle):0;}
}
