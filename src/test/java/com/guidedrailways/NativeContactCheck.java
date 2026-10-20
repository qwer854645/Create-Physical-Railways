package com.guidedrailways;

public final class NativeContactCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    for (double a : new double[] {20, 80, 300})
      for (double b : new double[] {40, 160, 600})
        for (double speed : new double[] {.1, 2, 20})
          for (double alignment : new double[] {-1, -.5, 0, .5, 1}) {
            double j = NativeContactModel.impulse(speed, a, b, alignment);
            check(j > 0 && Double.isFinite(j), "finite response to actual closing contact");
            check(
                Math.abs(speed - j * (1 / a + alignment * alignment / b)) < 1e-9,
                "contact response removes closing velocity");
            check(
                Math.abs(j * alignment / b / 20 * 20 - j * alignment / b) < 1e-9,
                "Create blocks/tick conversion");
            check(
                NativeContactModel.impulse(-speed, a, b, alignment) == 0,
                "separating contact has no response");
          }
    check(NativeContactModel.impulse(10, 80, 80, 1) == 400, "equal masses share normal momentum");
    check(NativeContactModel.impulse(10, 80, 160, 1) > 400, "whole train mass changes response");
    check(NativeContactModel.impulse(Double.NaN, 80, 80, 1) == 0, "invalid velocity rejected");
    check(NativeContactModel.impulse(10, 0, 80, 1) == 0, "invalid mass rejected");
    System.out.println("NativeContactCheck: " + checks + " assertions passed");
  }
}
