package com.guidedrailways;

/** Stable saved identifiers; independent of optional mod classes. */
public enum RailGauge {
    STANDARD, NARROW, WIDE, MONORAIL;
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
}
