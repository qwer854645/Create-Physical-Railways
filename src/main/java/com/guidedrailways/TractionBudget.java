package com.guidedrailways;

/**
 * Shares the consist acceleration ceiling between actual motors; RPM force caps remain independent.
 */
public final class TractionBudget {
  private TractionBudget() {}

  public static double[] shares(double totalMass, double[] rpm) {
    var result = new double[rpm.length];
    if (!Double.isFinite(totalMass) || totalMass <= 0) return result;
    double total = 0;
    for (double input : rpm)
      if (Double.isFinite(input) && Math.abs(input) > .001) total += Math.abs(input);
    if (total <= 0) return result;
    for (int i = 0; i < rpm.length; i++)
      if (Double.isFinite(rpm[i]) && Math.abs(rpm[i]) > .001)
        result[i] = totalMass * Math.abs(rpm[i]) / total;
    return result;
  }
}
