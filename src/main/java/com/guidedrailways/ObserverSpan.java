package com.guidedrailways;
/** Finite, clipped distances on one directed track edge. */
public record ObserverSpan(double low,double high) {
    public static ObserverSpan between(double a,double b,double length){
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(length)||length<=0)return null;
        return new ObserverSpan(Math.clamp(Math.min(a,b),0,length),Math.clamp(Math.max(a,b),0,length));
    }
    public boolean contains(double position){return Double.isFinite(position)&&position>=low-1e-5&&position<=high+1e-5;}
}
