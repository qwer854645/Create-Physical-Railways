package com.guidedrailways;

/** Stable saved identifiers; independent of optional mod classes. */
public enum RailGauge {
    STANDARD, NARROW, WIDE, MONORAIL;
    public double widthScale(){return switch(this){case NARROW->.5;case WIDE->1.5;case MONORAIL->.5;default->1;};}
    public boolean accepts(String trackType){
        if("railways:universal".equals(trackType))return this!=MONORAIL;
        return switch(this){
            case STANDARD->"create:standard".equals(trackType);
            case NARROW->"railways:narrow_gauge".equals(trackType);
            case WIDE->"railways:wide_gauge".equals(trackType);
            case MONORAIL->"railways:monorail".equals(trackType);
        };
    }
    public static RailGauge read(String value){try{return valueOf(value);}catch(IllegalArgumentException ignored){return STANDARD;}}
    /** Universal track chooses the standard profile; unknown track types remain unplaceable. */
    public static RailGauge forTrack(String type){for(var gauge:values())if(gauge.accepts(type))return gauge;return null;}
}
