package com.guidedrailways;
/** Alignment measured from Create 6.0.10 bogey_wheel.obj and z_ortho.json.
 * The polygon tread has support heights .387426..41934685 across its spin phases;
 * use the minimum contact height to prevent a visible air gap. Rail top is 5.54375/16.
 * StandardBogeyRenderer.Small places the axle at y=.75 relative to its render origin. */
public final class BogieGeometry {
    private BogieGeometry(){}
    public static final double TREAD_RADIUS=.387426,RAIL_TOP=5.54375/16;
    public static final double RENDER_Y=-.5,AXLE_RENDER_Y=.75;
    public static final double RIDE_HEIGHT=RAIL_TOP+TREAD_RADIUS+.5-RENDER_Y-AXLE_RENDER_Y;
    /** Create flat track graph points are at the rail block's bottom face. */
    public static final double MOUNT_TOP=2,MOUNT_UNDERSIDE=.75;
    public static final double PLATE_OFFSET=MOUNT_TOP-.5-RIDE_HEIGHT;
    public static final double PIVOT_HEIGHT=PLATE_OFFSET+MOUNT_UNDERSIDE-.5;
    public static final double SUPPORT_BOTTOM=5.742566/16;
}
