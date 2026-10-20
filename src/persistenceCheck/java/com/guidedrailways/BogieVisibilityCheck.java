package com.guidedrailways;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Actual Minecraft frustum and full model envelopes, without a world or graphics context. */
public final class BogieVisibilityCheck {
  public static void main(String[] args) {
    int checks = 0;
    for (var variant : BogeyVariant.values()) {
      var b = BogieRenderEnvelope.get(variant);
      if (b != BogieRenderEnvelope.get(variant)) throw new AssertionError("envelope rebuilt");
      for (var box : BogieCollision.boxes(variant)) {
        if (box.x0() < b.x0()
            || box.y0() < b.y0()
            || box.z0() < b.z0()
            || box.x1() > b.x1()
            || box.y1() > b.y1()
            || box.z1() > b.z1()) throw new AssertionError("model extends outside render bounds");
        checks++;
      }
    }
    var frustum = new Frustum(new Matrix4f(), new Matrix4f());
    frustum.prepare(0, 0, 0);
    var b = BogieRenderEnvelope.get(BogeyVariant.DEFAULT);
    if (frustum.isVisible(new AABB(1.5, 0, 0, 2.5, 1, 1)))
      throw new AssertionError("control root block should be offscreen");
    if (!frustum.isVisible(new AABB(b.x0() + 1.5, b.y0(), b.z0(), b.x1() + 1.5, b.y1(), b.z1())))
      throw new AssertionError("visible wheel lost at view edge");
    if (!RenderVisibility.within(120, 0, 0, 140, 1, 1, 0, 0, 0, 128))
      throw new AssertionError("visible nose lost because origin exceeds view distance");
    if (RenderVisibility.within(140, 0, 0, 160, 1, 1, 0, 0, 0, 128))
      throw new AssertionError("off-range geometry is still rendered");
    if (!RenderVisibility.within(100, 0, 0, 101, 1, 1, 0, 0, 0, 256))
      throw new AssertionError("default 64-block cutoff survived");
    if (!RenderVisibility.within(
        1280100, 0, 1280000, 1280101, 1, 1280001, 1280000, 0, 1280000, 256))
      throw new AssertionError("plot coordinates fail range test");
    System.out.println(
        "BogieVisibilityCheck: "
            + checks
            + " model boxes covered; actual Minecraft edge frustum, distance and plot-space cases passed");
  }
}
