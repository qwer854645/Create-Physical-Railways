package com.guidedrailways;

/** Stable block-state indices. Only geometrically adapted variants are admitted at placement. */
public enum BogeyVariant {
    DEFAULT("", ""),FREIGHT("railways:freight","freight"),PASSENGER("railways:passenger","passenger"),
    MODERN("railways:modern","modern"),BLOMBERG("railways:blomberg","blomberg"),Y25("railways:y25","y25");
    public final String id,frame;
    BogeyVariant(String id,String frame){this.id=id;this.frame=frame;}
    public static BogeyVariant read(int index){return index>=0&&index<values().length?values()[index]:DEFAULT;}
    public static BogeyVariant find(String id){for(var value:values())if(value.id.equals(id))return value;return null;}
}
