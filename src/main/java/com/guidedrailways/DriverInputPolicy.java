package com.guidedrailways;
/** Narrow input ownership policy shared by networking and client regression checks. */
public final class DriverInputPolicy {
    private DriverInputPolicy(){}
    public static boolean remoteCommand(int id){return id==1||id==2||id==8||id==9||id==19||id>=10&&id<=22;}
    public static boolean permittedWhileObserving(int id){return id==0||id==8||id==19||id==35;}
    public static boolean suppressMovement(boolean active,boolean gui){return active&&!gui;}
    public static boolean suppressLook(boolean active,boolean dragging,boolean gui,boolean rightDown){return active&&dragging&&!gui&&rightDown;}
    /** Current pinned Minecraft turn equation: sensitivity*.6F+.2F. Per-event gain, never a saved option. */
    public static double heldMouseSensitivity(){return -(double).2F/(double).6F;}
    public static int steer(boolean left,boolean right){return left==right?0:left?-1:1;}
}
