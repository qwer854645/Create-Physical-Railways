package com.guidedrailways;

public final class ObserverSpanCheck {
  private static void require(boolean condition, String why) {
    if (!condition) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    var forward = ObserverSpan.between(2, 8, 10);
    var reverse = ObserverSpan.between(8, 2, 10);
    require(forward.equals(reverse), "reverse crossing covers same positions");
    for (double p : new double[] {2, 3, 5, 8})
      require(forward.contains(p), "fast crossing cannot skip point " + p);
    require(!forward.contains(1) && !forward.contains(9), "do not trigger outside covered span");
    var clipped = ObserverSpan.between(-10, 20, 10);
    require(clipped.low() == 0 && clipped.high() == 10, "clip to edge");
    require(ObserverSpan.between(4, 4, 10).contains(4), "stationary footprint remains active");
    require(
        !forward.contains(Double.NaN) && !forward.contains(Double.POSITIVE_INFINITY),
        "invalid point");
    require(ObserverSpan.between(Double.NaN, 1, 10) == null, "invalid sweep");
    require(ObserverSpan.between(1, 2, 0) == null, "invalid edge");
    System.out.println("Observer span checks passed");
  }
}
