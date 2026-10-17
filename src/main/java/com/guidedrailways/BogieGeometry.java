package com.guidedrailways;
/** Alignment measured from Create 6.0.10 bogey_wheel.obj and z_ortho.json.
 * The tread outer ring at |x|=1.0625 has radius 0.41934685; rail top is 5.54375/16.
 * StandardBogeyRenderer.Small places the axle at y=.75 relative to its render origin. */
public final class BogieGeometry {
    private BogieGeometry(){}
    public static final double TREAD_RADIUS=.41934685,RAIL_TOP=5.54375/16;
    public static final double RENDER_Y=-.5,AXLE_RENDER_Y=.75;
    public static final double RIDE_HEIGHT=RAIL_TOP+TREAD_RADIUS+.5-RENDER_Y-AXLE_RENDER_Y;
}
